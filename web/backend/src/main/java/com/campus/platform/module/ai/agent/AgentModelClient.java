package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.AiConfigHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Sends one Chat Completions turn and preserves the assistant message for the next turn. */
@Component
public class AgentModelClient {
    private static final MediaType JSON = MediaType.get("application/json");
    private static final long MAX_RESPONSE_BYTES = 1024 * 1024;

    private final AiConfigHolder config;
    private final ObjectMapper json;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    public AgentModelClient(AiConfigHolder config, ObjectMapper json) {
        this.config = config;
        this.json = json;
    }

    public ObjectNode complete(List<ObjectNode> messages, List<ObjectNode> tools, Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw failure("Agent 调用已超时");
        }
        String apiKey = config.getApiKey();
        String model = config.getModel();
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("sk-xxx") || apiKey.startsWith("${")
                || model == null || model.isBlank()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED, "请先配置 Agent 使用的模型和凭据");
        }

        ObjectNode body = json.createObjectNode().put("model", model)
                .put("stream", false).put("max_tokens", config.getMaxTokens());
        body.putArray("messages").addAll(messages);
        if (!tools.isEmpty()) {
            body.putArray("tools").addAll(tools);
            body.put("tool_choice", "auto");
        }

        Request request = new Request.Builder().url(completionUrl(config.getBaseUrl()))
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        var call = http.newCall(request);
        call.timeout().timeout(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                throw failure("模型接口返回 HTTP " + response.code());
            }
            if (response.body() == null) {
                throw failure("模型接口未返回响应");
            }
            var source = response.body().source();
            if (source.request(MAX_RESPONSE_BYTES + 1)) {
                throw failure("模型响应超过大小限制");
            }
            JsonNode root = json.readTree(source.readUtf8());
            return assistantMessage(root);
        } catch (IOException e) {
            throw failure("模型请求超时、连接失败或返回了无法解析的数据");
        }
    }

    private HttpUrl completionUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED, "请先配置模型服务地址");
        }
        String base = baseUrl.strip().replaceAll("/+$", "");
        String endpoint = base.endsWith("/chat/completions") ? base
                : base.endsWith("/v1") ? base + "/chat/completions" : base + "/v1/chat/completions";
        HttpUrl url = HttpUrl.parse(endpoint);
        if (url == null || url.query() != null || url.fragment() != null
                || !url.username().isEmpty() || !url.password().isEmpty()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED, "模型服务地址无效");
        }
        return url;
    }

    private ObjectNode assistantMessage(JsonNode root) {
        JsonNode choice = root == null ? null : root.path("choices").path(0);
        if (choice == null || !(choice.path("message") instanceof ObjectNode message)
                || !"assistant".equals(message.path("role").asText())) {
            throw failure("模型未返回有效的助手消息");
        }

        String reason = choice.path("finish_reason").asText();
        JsonNode calls = message.path("tool_calls");
        if ("tool_calls".equals(reason) && calls instanceof ArrayNode array && !array.isEmpty()) {
            validateToolCalls(array);
        } else if (!"stop".equals(reason) || (calls.isArray() && !calls.isEmpty())
                || (!calls.isMissingNode() && !calls.isNull() && !calls.isArray())
                || !isNonBlankString(message.path("content"))) {
            throw failure("模型未返回完整答案或有效的工具调用");
        }
        return message;
    }

    private void validateToolCalls(ArrayNode calls) {
        Set<String> ids = new HashSet<>();
        for (JsonNode call : calls) {
            JsonNode id = call.path("id");
            JsonNode function = call.path("function");
            if (!isNonBlankString(id) || !ids.add(id.asText()) || !"function".equals(call.path("type").asText())
                    || !isNonBlankString(function.path("name")) || !function.path("arguments").isTextual()) {
                throw failure("模型返回了无效的工具调用");
            }
        }
    }

    private boolean isNonBlankString(JsonNode value) {
        return value.isTextual() && !value.asText().isBlank();
    }

    private BizException failure(String message) {
        return new BizException(ResultCode.AI_INVOKE_FAIL, message);
    }
}
