package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.AiConfigHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentModelClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private final BlockingQueue<JsonNode> requests = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> responses = new LinkedBlockingQueue<>();
    private HttpServer server;
    private AiConfigHolder config;
    private AgentModelClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                requests.add(json.readTree(exchange.getRequestBody()));
                String response = responses.remove();
                int status = response.equals("UNAUTHORIZED") ? 401 : 200;
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        config = mock(AiConfigHolder.class);
        when(config.getBaseUrl()).thenReturn("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        when(config.getApiKey()).thenReturn("test-token");
        when(config.getModel()).thenReturn("test-tool-model");
        when(config.getMaxTokens()).thenReturn(512);
        client = new AgentModelClient(config, json);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void roundTripsAssistantToolCallAndMatchingJavaResult() {
        responses.add("""
                {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
                "reasoning_content":"Provider state",
                "tool_calls":[{"id":"call_1","type":"function",
                "function":{"name":"get_probe_code","arguments":"{}"}}]}}]}
                """);
        responses.add("""
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"QA-3419"}}]}
                """);
        ObjectNode tool = probeTool(json);
        ObjectNode user = json.createObjectNode().put("role", "user").put("content", "What is the probe code?");

        ObjectNode assistant = client.complete(List.of(user), List.of(tool), Duration.ofSeconds(5));
        String callId = assistant.path("tool_calls").get(0).path("id").asText();
        ObjectNode result = json.createObjectNode().put("role", "tool")
                .put("tool_call_id", callId).put("content", "QA-3419");
        ObjectNode answer = client.complete(List.of(user, assistant, result), List.of(tool), Duration.ofSeconds(5));

        JsonNode firstRequest = requests.remove();
        assertThat(firstRequest.path("tools").get(0)).isEqualTo(tool);
        assertThat(firstRequest.path("model").asText()).isEqualTo("test-tool-model");
        JsonNode secondRequest = requests.remove();
        assertThat(secondRequest.path("messages").get(1).path("reasoning_content").asText()).isEqualTo("Provider state");
        assertThat(secondRequest.path("messages").get(2)).isEqualTo(result);
        assertThat(answer.path("content").asText()).isEqualTo("QA-3419");
    }

    @Test
    void doesNotRetryAuthenticationFailuresOrIncludeProviderBody() {
        responses.add("UNAUTHORIZED");
        assertThatThrownBy(() -> client.complete(List.of(), List.of(), Duration.ofSeconds(5)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageNotContaining("UNAUTHORIZED");
        assertThat(requests).hasSize(1);
    }

    @Test
    void rejectsToolCallWithoutId() {
        responses.add("""
                {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
                "tool_calls":[{"type":"function","function":{"name":"get_probe_code","arguments":"{}"}}]}}]}
                """);
        assertThatThrownBy(() -> client.complete(List.of(), List.of(probeTool(json)), Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.AI_INVOKE_FAIL.getCode()));
    }

    @Test
    void timeoutDoesNotPreventTheNextRequest() {
        server.createContext("/slow/chat/completions", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exchange.sendResponseHeaders(204, -1);
            }
        });
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        when(config.getBaseUrl()).thenReturn(base + "/slow/chat/completions");
        assertThatThrownBy(() -> client.complete(List.of(), List.of(), Duration.ofMillis(40)))
                .isInstanceOf(BizException.class).hasMessageContaining("超时");

        when(config.getBaseUrl()).thenReturn(base + "/v1");
        responses.add("""
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"恢复成功"}}]}
                """);
        assertThat(client.complete(List.of(), List.of(), Duration.ofSeconds(5)).path("content").asText())
                .isEqualTo("恢复成功");
    }

    static ObjectNode probeTool(ObjectMapper json) {
        ObjectNode tool = json.createObjectNode().put("type", "function");
        ObjectNode fn = tool.putObject("function").put("name", "get_probe_code")
                .put("description", "Returns the current acceptance test code, which cannot be guessed.");
        fn.putObject("parameters").put("type", "object").put("additionalProperties", false).putObject("properties");
        return tool;
    }
}
