package com.campus.platform.module.ai.gateway;


import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.entity.AiCallLog;
import com.campus.platform.module.ai.entity.AiMessage;
import com.campus.platform.module.ai.entity.PdfDocument;
import com.campus.platform.module.ai.entity.PromptTemplate;
import com.campus.platform.module.ai.mapper.AiCallLogMapper;
import com.campus.platform.module.ai.mapper.PdfDocumentMapper;
import com.campus.platform.module.ai.mapper.PromptTemplateMapper;
import com.campus.platform.utils.RedisUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 针对当前 AiGatewayService 实现的同步调用测试。
 *
 * 当前生产实现直接依赖 OkHttp、PromptTemplateMapper 和 RedisUtils，
 * 因此测试使用本地 HTTP 服务验证真实请求，不再引用已删除的旧网关组件。
 */
@DisplayName("AI 网关同步调用")
class AiGatewayServiceTest {

    private static final Long UID = 1001L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile int responseStatus;
    private volatile String responseBody;

    private AiConfigHolder configHolder;
    private ChatMemoryService chatMemoryService;
    private SensitiveWordService sensitiveWordService;
    private PromptTemplateMapper promptTemplateMapper;
    private AiCallLogMapper aiCallLogMapper;
    private PdfDocumentMapper pdfDocumentMapper;
    private RedisUtils redisUtils;
    private AiGatewayService gateway;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();

        configHolder = mock(AiConfigHolder.class);
        chatMemoryService = mock(ChatMemoryService.class);
        sensitiveWordService = mock(SensitiveWordService.class);
        promptTemplateMapper = mock(PromptTemplateMapper.class);
        aiCallLogMapper = mock(AiCallLogMapper.class);
        pdfDocumentMapper = mock(PdfDocumentMapper.class);
        redisUtils = mock(RedisUtils.class);

        when(configHolder.getBaseUrl()).thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
        when(configHolder.getApiKey()).thenReturn("sk-test");
        when(configHolder.getModel()).thenReturn("deepseek-chat");
        when(configHolder.getTemperature()).thenReturn(0.7);
        when(configHolder.getMaxTokens()).thenReturn(2048);
        when(configHolder.getRetryTimes()).thenReturn(2);
        when(configHolder.getRateLimitPerDay()).thenReturn(50);
        when(sensitiveWordService.contains(anyString())).thenReturn(false);
        when(redisUtils.get(anyString())).thenReturn(null);
        when(chatMemoryService.getBySession(any())).thenReturn(List.of());

        responseStatus = 200;
        responseBody = successResponse("答案", 11, 22);
        gateway = new AiGatewayService(configHolder, chatMemoryService, sensitiveWordService,
                promptTemplateMapper, aiCallLogMapper, pdfDocumentMapper, redisUtils);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("达到每日限额时应拒绝调用模型")
    void chat_shouldRejectWhenRateLimited() {
        when(redisUtils.get(anyString())).thenReturn(50L);

        assertThatThrownBy(() -> gateway.chat(UID, Constants.SCENE_CHAT, "问题", null, null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.RATE_LIMITED.getCode());

        assertThat(requestCount.get()).isZero();
        verify(redisUtils, never()).incr(anyString(), eq(1L), eq(TimeUnit.DAYS));
    }

    @Test
    @DisplayName("敏感词应在 HTTP 调用前被拦截")
    void chat_shouldRejectSensitiveInput() {
        when(sensitiveWordService.contains("敏感问题")).thenReturn(true);

        assertThatThrownBy(() -> gateway.chat(UID, Constants.SCENE_CHAT, "敏感问题", null, null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.SENSITIVE_WORD.getCode());

        assertThat(requestCount.get()).isZero();
        verify(aiCallLogMapper, never()).insert(any(AiCallLog.class));
    }

    @Test
    @DisplayName("应按 system、历史、当前问题的顺序构建请求并保存问答")
    void chat_shouldBuildMessagesAndPersistQaPair() throws Exception {
        PromptTemplate template = new PromptTemplate();
        template.setContent("你是{subject}助教，{question}");
        when(promptTemplateMapper.selectOne(any())).thenReturn(template);

        AiMessage historyUser = new AiMessage();
        historyUser.setRole("user");
        historyUser.setContent("上一问");
        AiMessage historyAssistant = new AiMessage();
        historyAssistant.setRole("assistant");
        historyAssistant.setContent("上一答");
        when(chatMemoryService.getBySession(88L)).thenReturn(List.of(historyUser, historyAssistant));

        String answer = gateway.chat(UID, Constants.SCENE_CHAT, "当前问题", 88L, Map.of("subject", "算法"));

        assertThat(answer).isEqualTo("答案");
        JsonNode messages = OBJECT_MAPPER.readTree(requestBody.get()).path("messages");
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        assertThat(messages.get(0).path("content").asText()).isEqualTo("你是算法助教，");
        assertThat(messages.get(1).path("content").asText()).isEqualTo("上一问");
        assertThat(messages.get(2).path("content").asText()).isEqualTo("上一答");
        assertThat(messages.get(3).path("content").asText()).isEqualTo("当前问题");
        verify(chatMemoryService).saveMessage(88L, "user", "当前问题", null);
        verify(chatMemoryService).saveMessage(88L, "assistant", "答案", 22);
    }

    @Test
    @DisplayName("提示词参数为 null 时应安全替换为空串")
    void chat_shouldSafelyReplaceNullPromptParameter() throws Exception {
        PromptTemplate template = new PromptTemplate();
        template.setContent("科目：{subject}；主题：{topic}");
        when(promptTemplateMapper.selectOne(any())).thenReturn(template);
        Map<String, String> params = new HashMap<>();
        params.put("subject", "Java");
        params.put("topic", null);

        gateway.chat(UID, Constants.SCENE_OUTLINE, "生成提纲", null, params);

        JsonNode messages = OBJECT_MAPPER.readTree(requestBody.get()).path("messages");
        assertThat(messages.get(0).path("content").asText()).isEqualTo("科目：Java；主题：");
    }

    @Test
    @DisplayName("代码纠错场景应把代码和语言拼入用户消息")
    void chat_shouldBuildCodeFixQuestion() throws Exception {
        gateway.chat(UID, Constants.SCENE_CODE_FIX, "请修复", null,
                Map.of("code", "int a = ;", "language", "java"));

        JsonNode messages = OBJECT_MAPPER.readTree(requestBody.get()).path("messages");
        String userContent = messages.get(messages.size() - 1).path("content").asText();
        assertThat(userContent).contains("语言: java", "代码:\nint a = ;", "请修复");
        verify(chatMemoryService, never()).saveMessage(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("PDF 上下文只能注入当前用户拥有的文档")
    void chat_shouldInjectOwnedPdfContext() throws Exception {
        PdfDocument document = new PdfDocument();
        document.setId(7L);
        document.setUserId(UID);
        document.setTextContent("进程是资源分配的基本单位。");
        when(pdfDocumentMapper.selectById(7L)).thenReturn(document);

        gateway.chat(UID, Constants.SCENE_PDF, "进程是什么", null, Map.of("docId", "7"));

        JsonNode messages = OBJECT_MAPPER.readTree(requestBody.get()).path("messages");
        assertThat(messages.toString()).contains("以下是用户上传的PDF文档内容", "进程是资源分配的基本单位");
    }

    @Test
    @DisplayName("HTTP 失败应按配置重试并记录失败日志")
    void chat_shouldRetryAndWriteFailureLog() {
        responseStatus = 500;
        responseBody = "{\"error\":\"boom\"}";

        assertThatThrownBy(() -> gateway.chat(UID, Constants.SCENE_CHAT, "问题", null, null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.AI_INVOKE_FAIL.getCode());

        assertThat(requestCount.get()).isEqualTo(3);
        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    @DisplayName("成功调用应记录 token、模型和成功状态")
    void chat_shouldWriteSuccessLog() {
        gateway.chat(UID, Constants.SCENE_CHAT, "问题", null, null);

        var captor = org.mockito.ArgumentCaptor.forClass(AiCallLog.class);
        verify(aiCallLogMapper).insert(captor.capture());
        AiCallLog log = captor.getValue();
        assertThat(log.getUserId()).isEqualTo(UID);
        assertThat(log.getScene()).isEqualTo(Constants.SCENE_CHAT);
        assertThat(log.getModel()).isEqualTo("deepseek-chat");
        assertThat(log.getPromptTokens()).isEqualTo(11);
        assertThat(log.getCompletionTokens()).isEqualTo(22);
        assertThat(log.getStatus()).isZero();
    }

    @Test
    @DisplayName("每日限流键应包含用户和当天日期并预占一次额度")
    void chat_shouldReserveDailyQuota() {
        gateway.chat(UID, Constants.SCENE_CHAT, "问题", null, null);

        String expectedKey = Constants.REDIS_AI_RATE_LIMIT + UID + ":"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        verify(redisUtils).incr(expectedKey, 1, TimeUnit.DAYS);
    }

    private static String successResponse(String content, int promptTokens, int completionTokens) {
        return "{\"choices\":[{\"message\":{\"content\":\"" + content
                + "\"}}],\"usage\":{\"prompt_tokens\":" + promptTokens
                + ",\"completion_tokens\":" + completionTokens + "}}";
    }
}
