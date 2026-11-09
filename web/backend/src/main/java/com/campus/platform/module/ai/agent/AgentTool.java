package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public record AgentTool(String name, String description, Map<String, String> properties,
                        List<String> required, Handler handler) {
    public AgentTool(String name, String description, Map<String, String> properties, List<String> required,
                     BiFunction<Long, JsonNode, JsonNode> handler) {
        this(name, description, properties, required, (userId, args, deadline) -> handler.apply(userId, args));
    }

    public ObjectNode definition(ObjectMapper json) {
        ObjectNode tool = json.createObjectNode().put("type", "function");
        ObjectNode fn = tool.putObject("function").put("name", name).put("description", description);
        ObjectNode schema = fn.putObject("parameters").put("type", "object").put("additionalProperties", false);
        ObjectNode fields = schema.putObject("properties");
        properties.forEach((field, type) -> fields.putObject(field).put("type", type));
        var requiredFields = schema.putArray("required");
        required.forEach(requiredFields::add);
        return tool;
    }

    public JsonNode run(Long userId, JsonNode args) {
        return run(userId, args, System.nanoTime() + java.time.Duration.ofSeconds(25).toNanos());
    }

    JsonNode run(Long userId, JsonNode args, long deadline) {
        if (args == null || !args.isObject()) {
            throw new BizException(ResultCode.BAD_REQUEST, "工具参数必须是 JSON 对象");
        }
        args.fields().forEachRemaining(field -> {
            boolean valid = switch (properties.getOrDefault(field.getKey(), "")) {
                case "string" -> field.getValue().isTextual();
                case "integer" -> field.getValue().isIntegralNumber() && field.getValue().canConvertToLong();
                default -> false;
            };
            if (!valid) {
                throw new BizException(ResultCode.BAD_REQUEST, "工具参数字段或类型不正确");
            }
        });
        if (required.stream().anyMatch(field -> !args.hasNonNull(field))) {
            throw new BizException(ResultCode.BAD_REQUEST, "缺少必要的工具参数");
        }
        return handler.run(userId, args, deadline);
    }

    public interface Handler {
        JsonNode run(Long userId, JsonNode args, long deadline);
    }
}
