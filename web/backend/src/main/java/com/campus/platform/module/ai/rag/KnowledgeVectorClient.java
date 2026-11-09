package com.campus.platform.module.ai.rag;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "campus.agent.rag.enabled", havingValue = "true")
class KnowledgeVectorClient {
    private static final MediaType JSON = MediaType.get("application/json");
    private final OkHttpClient http = new OkHttpClient.Builder().callTimeout(Duration.ofSeconds(25))
            .followRedirects(false).build();
    private final ObjectMapper json;
    private final String embeddingUrl;
    private final String embeddingModel;
    private final String collectionUrl;
    private final double minScore;
    private final int maxQueryChars;
    private volatile boolean collectionReady;

    KnowledgeVectorClient(ObjectMapper json,
                          @Value("${campus.agent.rag.embedding-url:http://127.0.0.1:11434}") String embeddingUrl,
                          @Value("${campus.agent.rag.embedding-model:bge-m3}") String embeddingModel,
                          @Value("${campus.agent.rag.qdrant-url:http://127.0.0.1:6333}") String qdrantUrl,
                          @Value("${campus.agent.rag.collection:campus_bge_m3_v1}") String collection,
                          @Value("${campus.agent.rag.min-score:0.40}") double minScore,
                          @Value("${campus.agent.rag.query-chars:2200}") int maxQueryChars) {
        if (!collection.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("知识库集合名无效");
        }
        this.json = json;
        this.embeddingUrl = embeddingUrl.replaceAll("/+$", "") + "/api/embed";
        this.embeddingModel = embeddingModel;
        this.collectionUrl = qdrantUrl.replaceAll("/+$", "") + "/collections/" + collection;
        this.minScore = minScore;
        if (maxQueryChars < 160 || maxQueryChars > 3000) {
            throw new IllegalArgumentException("知识库查询长度须在 160 至 3000 字符之间");
        }
        this.maxQueryChars = maxQueryChars;
    }

    List<JsonNode> embed(List<String> input) {
        return embed(input, System.nanoTime() + Duration.ofSeconds(25).toNanos());
    }

    private List<JsonNode> embed(List<String> input, long deadline) {
        ObjectNode body = json.createObjectNode().put("model", embeddingModel).put("truncate", false);
        var texts = body.putArray("input");
        input.forEach(texts::add);
        JsonNode embeddings = request("POST", embeddingUrl, body, "向量模型", false, Set.of(), deadline).path("embeddings");
        if (!embeddings.isArray() || embeddings.size() != input.size()) {
            throw failure("向量模型返回了无效的向量");
        }
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode vector : embeddings) {
            if (!vector.isArray() || vector.isEmpty()) {
                throw failure("向量模型返回了空向量");
            }
            result.add(vector);
        }
        return result;
    }

    void upsert(long docId, long ownerId, String scope, String version, List<String> ids, List<JsonNode> vectors) {
        ensureCollection(vectors.get(0).size());
        ObjectNode body = json.createObjectNode();
        var points = body.putArray("points");
        for (int i = 0; i < ids.size(); i++) {
            var point = points.addObject().put("id", ids.get(i));
            point.set("vector", vectors.get(i));
            point.putObject("payload").put("doc_id", docId).put("owner_id", ownerId)
                    .put("scope", scope).put("version", version);
        }
        request("PUT", collectionUrl + "/points?wait=true", body, "向量数据库", false);
    }

    List<String> search(long userId, Long docId, String question, long deadline) {
        int end = question.offsetByCodePoints(0, Math.min(maxQueryChars, question.codePointCount(0, question.length())));
        JsonNode vector = embed(List.of(question.substring(0, end)), deadline).get(0);
        ObjectNode body = json.createObjectNode().put("limit", 15).put("score_threshold", minScore);
        body.set("query", vector);
        var filter = body.putObject("filter");
        var access = filter.putArray("should");
        access.add(condition("scope", "PUBLIC"));
        var privateAccess = access.addObject().putArray("must");
        privateAccess.add(condition("scope", "PRIVATE"));
        privateAccess.add(condition("owner_id", userId));
        if (docId != null) {
            filter.putArray("must").add(condition("doc_id", docId));
        }
        var response = request("POST", collectionUrl + "/points/query", body, "向量数据库", true, Set.of(), deadline);
        if (response == null) {
            return List.of();
        }
        var points = response.path("result").path("points");
        List<String> ids = new ArrayList<>();
        points.forEach(point -> ids.add(point.path("id").asText()));
        return ids;
    }

    void delete(long docId, String version) {
        ObjectNode body = json.createObjectNode();
        var must = body.putObject("filter").putArray("must");
        must.add(condition("doc_id", docId));
        if (version != null) {
            must.add(condition("version", version));
        }
        request("POST", collectionUrl + "/points/delete?wait=true", body, "向量数据库", true);
    }

    private ObjectNode condition(String key, Object value) {
        ObjectNode condition = json.createObjectNode().put("key", key);
        condition.putObject("match").set("value", json.valueToTree(value));
        return condition;
    }

    private synchronized void ensureCollection(int size) {
        if (collectionReady) {
            return;
        }
        var current = request("GET", collectionUrl, null, "向量数据库", true);
        if (current == null) {
            ObjectNode definition = json.createObjectNode();
            definition.putObject("vectors").put("size", size).put("distance", "Cosine");
            request("PUT", collectionUrl, definition, "向量数据库", false, Set.of(409));
            current = request("GET", collectionUrl, null, "向量数据库", false);
        }
        if (current.at("/result/config/params/vectors/size").asInt(-1) != size) {
            throw failure("向量维度不匹配，请使用对应的知识库集合并重建索引");
        }
        collectionReady = true;
    }

    private JsonNode request(String method, String url, ObjectNode body, String service, boolean allow404) {
        return request(method, url, body, service, allow404, Set.of());
    }

    private JsonNode request(String method, String url, ObjectNode body, String service, boolean allow404,
                             Set<Integer> acceptedCodes) {
        return request(method, url, body, service, allow404, acceptedCodes,
                System.nanoTime() + Duration.ofSeconds(25).toNanos());
    }

    private JsonNode request(String method, String url, ObjectNode body, String service, boolean allow404,
                             Set<Integer> acceptedCodes, long deadline) {
        long remaining = Math.min(Duration.ofSeconds(25).toNanos(), deadline - System.nanoTime());
        if (remaining <= 0) {
            throw failure("知识库查询已超时");
        }
        Request request = new Request.Builder().url(url)
                .method(method, body == null ? null : RequestBody.create(body.toString(), JSON)).build();
        var call = http.newCall(request);
        call.timeout().timeout(remaining, TimeUnit.NANOSECONDS);
        try (var response = call.execute()) {
            if ((allow404 && response.code() == 404) || acceptedCodes.contains(response.code())) {
                return null;
            }
            if (!response.isSuccessful() || response.body() == null) {
                throw failure(service + "返回 HTTP " + response.code());
            }
            return json.readTree(response.body().byteStream());
        } catch (IOException e) {
            throw failure(service + "不可用或响应超时");
        }
    }

    private BizException failure(String message) {
        return new BizException(ResultCode.AI_INVOKE_FAIL, message);
    }
}
