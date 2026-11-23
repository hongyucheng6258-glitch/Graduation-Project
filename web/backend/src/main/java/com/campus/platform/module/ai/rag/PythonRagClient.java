package com.campus.platform.module.ai.rag;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.mapper.KnowledgeMapper.ChunkRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 调用 Python 检索服务（web/ai-service）。
 *
 * 只有 {@code campus.agent.rag.provider=python} 时才会被 KnowledgeService 使用；
 * 默认 local 时本类不参与任何请求，系统行为与加开关前完全一致。
 * 本类不写 MySQL：切片正文仍由 KnowledgeService 落库，保证单一真源。
 */
@Component
class PythonRagClient {
    private static final MediaType JSON = MediaType.get("application/json");
    private final OkHttpClient http = new OkHttpClient.Builder().followRedirects(false).build();
    private final ObjectMapper json;
    private final boolean enabled;
    private final boolean fallback;
    private final String baseUrl;
    private final String token;
    private final int chunkChars;
    private final long searchTimeoutMs;
    private final long indexTimeoutMs;

    PythonRagClient(ObjectMapper json,
                    @Value("${campus.agent.rag.provider:local}") String provider,
                    @Value("${campus.agent.rag.fallback:true}") boolean fallback,
                    @Value("${campus.agent.rag.chunk-chars:640}") int chunkChars,
                    @Value("${campus.ai-service.url:http://ai-service:8000}") String baseUrl,
                    @Value("${campus.ai-service.token:}") String token,
                    @Value("${campus.ai-service.search-timeout-ms:25000}") long searchTimeoutMs,
                    @Value("${campus.ai-service.index-timeout-ms:120000}") long indexTimeoutMs) {
        this.json = json;
        this.enabled = "python".equalsIgnoreCase(provider);
        this.fallback = fallback;
        this.chunkChars = chunkChars;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
        this.searchTimeoutMs = searchTimeoutMs;
        this.indexTimeoutMs = indexTimeoutMs;
        if (enabled && token.isBlank()) {
            throw new IllegalArgumentException("启用 Python 检索服务时必须配置 campus.ai-service.token");
        }
    }

    boolean enabled() {
        return enabled;
    }

    boolean fallback() {
        return fallback;
    }

    List<ChunkRow> index(long documentId, long ownerId, String scope, String version,
                         byte[] content, long deadline) {
        ObjectNode body = json.createObjectNode()
                .put("documentId", documentId)
                .put("ownerId", ownerId)
                .put("scope", scope)
                .put("version", version)
                .put("chunkChars", chunkChars)
                .put("fileBase64", Base64.getEncoder().encodeToString(content));
        JsonNode response = request("POST", "/internal/rag/index", body, indexTimeoutMs, deadline);
        var chunks = response.path("chunks");
        if (!chunks.isArray() || chunks.isEmpty()) {
            throw failure("AI 检索服务未返回切片");
        }
        List<ChunkRow> rows = new ArrayList<>(chunks.size());
        for (JsonNode chunk : chunks) {
            String id = chunk.path("id").asText();
            String text = chunk.path("text").asText();
            int page = chunk.path("page").asInt();
            if (id.isBlank() || text.isBlank() || page < 1) {
                throw failure("AI 检索服务返回了无效切片");
            }
            rows.add(new ChunkRow(id, documentId, version, page, text));
        }
        return rows;
    }

    List<String> search(long userId, Long docId, String question, long deadline, boolean internal) {
        ObjectNode body = json.createObjectNode()
                .put("question", question)
                .put("userId", userId)
                .put("internal", internal);
        if (docId != null) {
            body.put("documentId", docId);
        }
        JsonNode response = request("POST", "/internal/rag/search", body, searchTimeoutMs, deadline);
        var ids = response.path("ids");
        if (!ids.isArray()) {
            throw failure("AI 检索服务返回了无效检索结果");
        }
        List<String> result = new ArrayList<>(ids.size());
        ids.forEach(id -> result.add(id.asText()));
        return result;
    }

    void delete(long docId, String version) {
        ObjectNode body = json.createObjectNode().put("documentId", docId);
        if (version != null) {
            body.put("version", version);
        }
        long deadline = System.nanoTime() + Duration.ofMillis(searchTimeoutMs).toNanos();
        request("DELETE", "/internal/rag/collection", body, searchTimeoutMs, deadline);
    }

    private JsonNode request(String method, String path, ObjectNode body, long timeoutMs, long deadline) {
        long remaining = Math.min(Duration.ofMillis(timeoutMs).toNanos(), deadline - System.nanoTime());
        if (remaining <= 0) {
            throw failure("AI 检索服务调用已超时");
        }
        Request request = new Request.Builder().url(baseUrl + path)
                .header("X-Service-Token", token)
                .method(method, RequestBody.create(body.toString(), JSON))
                .build();
        var call = http.newCall(request);
        call.timeout().timeout(remaining, TimeUnit.NANOSECONDS);
        try (var response = call.execute()) {
            String payload = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw failure("AI 检索服务返回 HTTP " + response.code() + extractError(payload));
            }
            if (payload.isBlank()) {
                throw failure("AI 检索服务返回了空响应");
            }
            return json.readTree(payload);
        } catch (IOException e) {
            throw failure("AI 检索服务不可用或响应超时");
        }
    }

    private String extractError(String payload) {
        try {
            String message = json.readTree(payload).path("error").asText();
            return message.isBlank() ? "" : "：" + message;
        } catch (IOException ignored) {
            return "";
        }
    }

    private BizException failure(String message) {
        return new BizException(ResultCode.AI_INVOKE_FAIL, message);
    }
}
