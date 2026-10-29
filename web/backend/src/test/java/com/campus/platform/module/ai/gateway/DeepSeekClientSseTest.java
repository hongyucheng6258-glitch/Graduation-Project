package com.campus.platform.module.ai.gateway;


import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.module.ai.entity.AiCallLog;
import com.campus.platform.module.ai.mapper.AiCallLogMapper;
import com.campus.platform.module.ai.mapper.PdfDocumentMapper;
import com.campus.platform.module.ai.mapper.PromptTemplateMapper;
import com.campus.platform.utils.RedisUtils;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 当前 AiGatewayService 的 SSE 真实链路测试。
 *
 * 旧 DeepSeekClient 已被移除，流式解析逻辑现在位于 AiGatewayService，
 * 因此测试直接覆盖公开的 streamChat 方法。
 */
@DisplayName("AI 网关 SSE 流式出口")
class DeepSeekClientSseTest {

    private static final Long UID = 1001L;

    private HttpServer server;
    private volatile int responseStatus;
    private volatile String sseBody;

    private ChatMemoryService chatMemoryService;
    private SensitiveWordService sensitiveWordService;
    private AiCallLogMapper aiCallLogMapper;
    private RedisUtils redisUtils;
    private AiGatewayService gateway;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] bytes = sseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
                output.flush();
            }
        });
        server.start();

        AiConfigHolder configHolder = mock(AiConfigHolder.class);
        chatMemoryService = mock(ChatMemoryService.class);
        sensitiveWordService = mock(SensitiveWordService.class);
        PromptTemplateMapper promptTemplateMapper = mock(PromptTemplateMapper.class);
        aiCallLogMapper = mock(AiCallLogMapper.class);
        PdfDocumentMapper pdfDocumentMapper = mock(PdfDocumentMapper.class);
        redisUtils = mock(RedisUtils.class);

        when(configHolder.getBaseUrl()).thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
        when(configHolder.getApiKey()).thenReturn("sk-test");
        when(configHolder.getModel()).thenReturn("deepseek-chat");
        when(configHolder.getTemperature()).thenReturn(0.7);
        when(configHolder.getMaxTokens()).thenReturn(2048);
        when(configHolder.getTimeoutMs()).thenReturn(5_000);
        when(configHolder.getRateLimitPerDay()).thenReturn(50);
        when(sensitiveWordService.contains(anyString())).thenReturn(false);
        when(redisUtils.get(anyString())).thenReturn(null);
        when(chatMemoryService.getBySession(any())).thenReturn(List.of());

        responseStatus = 200;
        sseBody = delta("校") + delta("园") + "data: [DONE]\n\n";
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
    @DisplayName("正常流应按序发送 delta，并以 done 完成")
    void streamChat_shouldEmitDeltasInOrderAndComplete() {
        RecordingEmitter emitter = new RecordingEmitter();
        SseEmitter returned = gateway.streamChat(UID, Constants.SCENE_CHAT, "你好", 88L, null, emitter);

        assertThat(returned).isSameAs(emitter);
        await().atMost(10, TimeUnit.SECONDS).until(() -> emitter.completeCount.get() == 1);
        assertThat(emitter.sendCount.get()).isEqualTo(3);
        verify(chatMemoryService).saveMessage(88L, "user", "你好", null);
        verify(chatMemoryService).saveMessage(88L, "assistant", "校园", null);
        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    @DisplayName("服务端不发 DONE 直接关闭时仍应保存完整回复一次")
    void streamChat_serverClosesWithoutDone_shouldStillCompleteOnce() {
        sseBody = delta("半") + delta("句");
        RecordingEmitter emitter = new RecordingEmitter();
        gateway.streamChat(UID, Constants.SCENE_CHAT, "问题", 88L, null, emitter);

        await().atMost(10, TimeUnit.SECONDS).until(() -> emitter.completeCount.get() == 1);
        verify(chatMemoryService).saveMessage(88L, "assistant", "半句", null);
        verify(chatMemoryService, times(2)).saveMessage(any(), anyString(), anyString(), any());
        assertThat(emitter.errorCount.get()).isZero();
    }

    @Test
    @DisplayName("HTTP 错误应发送错误事件并记录失败日志")
    void streamChat_httpError_shouldRecordFailure() {
        responseStatus = 500;
        sseBody = "{\"error\":\"boom\"}";
        RecordingEmitter emitter = new RecordingEmitter();
        gateway.streamChat(UID, Constants.SCENE_CHAT, "问题", null, null, emitter);

        await().atMost(10, TimeUnit.SECONDS).until(() -> emitter.completeCount.get() == 1);
        assertThat(emitter.sendCount.get()).isEqualTo(1);
        verify(chatMemoryService, never()).saveMessage(any(), anyString(), anyString(), any());
        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    @DisplayName("敏感词应同步抛异常且不建立 HTTP 请求")
    void streamChat_shouldRejectSensitiveInput() {
        when(sensitiveWordService.contains("敏感问题")).thenReturn(true);

        assertThatThrownBy(() -> gateway.streamChat(UID, Constants.SCENE_CHAT, "敏感问题", null, null))
                .isInstanceOf(BizException.class);

        verify(aiCallLogMapper, never()).insert(any(AiCallLog.class));
        verify(redisUtils).incr(anyString(), org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(TimeUnit.DAYS));
    }

    private static String delta(String content) {
        return "data: {\"choices\":[{\"delta\":{\"content\":\"" + content + "\"}}]}\n\n";
    }

    private static class RecordingEmitter extends SseEmitter {
        private final AtomicInteger sendCount = new AtomicInteger();
        private final AtomicInteger completeCount = new AtomicInteger();
        private final AtomicInteger errorCount = new AtomicInteger();

        private RecordingEmitter() {
            super(10_000L);
        }

        @Override
        public void send(SseEventBuilder builder) {
            sendCount.incrementAndGet();
        }

        @Override
        public void complete() {
            completeCount.incrementAndGet();
        }

        @Override
        public void completeWithError(Throwable ex) {
            errorCount.incrementAndGet();
        }
    }
}
