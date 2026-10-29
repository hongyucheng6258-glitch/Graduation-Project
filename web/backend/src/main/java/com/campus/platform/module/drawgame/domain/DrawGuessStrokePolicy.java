package com.campus.platform.module.drawgame.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Validates and normalizes small, viewport-independent drawing events. */
public final class DrawGuessStrokePolicy {
    public static final int MAX_POINTS = 64;
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    private DrawGuessStrokePolicy() { }

    public static Map<String, Object> validate(JsonNode payload) {
        if (payload == null || !payload.isObject()) throw new IllegalArgumentException("画笔数据格式错误");
        JsonNode pointsNode = payload.get("points");
        if (pointsNode == null || !pointsNode.isArray() || pointsNode.isEmpty()
                || pointsNode.size() > MAX_POINTS) {
            throw new IllegalArgumentException("笔画点数必须在1到64之间");
        }

        List<Map<String, Double>> points = new ArrayList<>(pointsNode.size());
        for (JsonNode point : pointsNode) {
            double x = coordinate(point, "x");
            double y = coordinate(point, "y");
            points.add(Map.of("x", x, "y", y));
        }

        String color = requiredText(payload, "color");
        if (!HEX_COLOR.matcher(color).matches()) throw new IllegalArgumentException("颜色格式不受支持");
        JsonNode widthNode = payload.get("width");
        if (widthNode == null || !widthNode.isNumber()) throw new IllegalArgumentException("笔画粗细无效");
        double width = widthNode.asDouble();
        if (!Double.isFinite(width) || width < 1 || width > 32) {
            throw new IllegalArgumentException("笔画粗细必须在1到32之间");
        }

        String tool = requiredText(payload, "tool");
        if (!"pen".equals(tool) && !"eraser".equals(tool)) {
            throw new IllegalArgumentException("画笔工具不受支持");
        }
        return Map.of("points", List.copyOf(points), "color", color,
                "width", width, "tool", tool);
    }

    private static double coordinate(JsonNode point, String key) {
        if (point == null || !point.isObject()) throw new IllegalArgumentException("笔画坐标格式错误");
        JsonNode value = point.get(key);
        if (value == null || !value.isNumber()) throw new IllegalArgumentException("笔画坐标格式错误");
        double coordinate = value.asDouble();
        if (!Double.isFinite(coordinate) || coordinate < 0 || coordinate > 1) {
            throw new IllegalArgumentException("笔画坐标超出画布范围");
        }
        return coordinate;
    }

    private static String requiredText(JsonNode payload, String key) {
        JsonNode value = payload.get(key);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("画笔数据缺少" + key);
        }
        return value.asText();
    }
}
