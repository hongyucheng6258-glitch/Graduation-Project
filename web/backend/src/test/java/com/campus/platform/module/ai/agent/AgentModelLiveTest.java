package com.campus.platform.module.ai.agent;

import com.campus.platform.module.ai.gateway.AiConfigHolder;
import com.campus.platform.module.ai.mapper.AiConfigMapper;
import com.campus.platform.utils.RedisCache;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "CAMPUS_AGENT_LIVE", matches = "1")
class AgentModelLiveTest {
    @Test
    void realModelRequestsJavaToolAndUsesItsResult() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var cache = mock(RedisCache.class);
        when(cache.get(anyString(), any(), any(), any())).thenReturn(java.util.Map.of());
        AiConfigHolder config = new AiConfigHolder(mock(AiConfigMapper.class), cache);
        ReflectionTestUtils.setField(config, "defaultBaseUrl", requiredEnv("AI_BASE_URL"));
        ReflectionTestUtils.setField(config, "defaultModel", requiredEnv("AI_MODEL"));
        ReflectionTestUtils.setField(config, "defaultMaxTokens", 2048);
        AgentModelClient client = new AgentModelClient(config, json);
        var tools = List.of(AgentModelClientTest.probeTool(json));
        ObjectNode user = json.createObjectNode().put("role", "user")
                .put("content", "请调用 get_probe_code 获取当前验收码，然后在最终答复中原样写出返回的验收码。");

        ObjectNode first = client.complete(List.of(user), tools, Duration.ofSeconds(30));
        assertThat(first.path("tool_calls").size()).as("模型应该发出一个真实工具调用").isEqualTo(1);
        JsonNode call = first.path("tool_calls").get(0);
        assertThat(call.path("function").path("name").asText()).isEqualTo("get_probe_code");
        JsonNode arguments = json.readTree(call.path("function").path("arguments").asText());
        assertThat(arguments.isObject() && arguments.isEmpty()).as("验收工具没有输入参数").isTrue();

        String code = "CAMPUS-" + UUID.randomUUID().toString().substring(0, 8);
        ObjectNode toolResult = json.createObjectNode().put("role", "tool")
                .put("tool_call_id", call.path("id").asText()).put("content", code);
        ObjectNode last = client.complete(List.of(user, first, toolResult), List.of(), Duration.ofSeconds(30));
        assertThat(last.path("content").asText()).contains(code);
    }

    private String requiredEnv(String name) {
        String value = System.getenv(name);
        assertThat(value != null && !value.isBlank()).as("请在本机设置 " + name).isTrue();
        return value;
    }
}
