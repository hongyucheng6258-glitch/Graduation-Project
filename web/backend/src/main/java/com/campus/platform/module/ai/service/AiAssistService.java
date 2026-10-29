package com.campus.platform.module.ai.service;

import cn.hutool.core.util.StrUtil;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.dto.AssistComposeDTO;
import com.campus.platform.module.ai.dto.AssistPolishDTO;
import com.campus.platform.module.ai.gateway.AiGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 辅助发布服务：发布活动 / 闲置 / 失物招领 / 动态时，
 * 辅助生成草稿（标题+正文+标签）与润色（润色/扩写/精简）。
 * 走后台 internalChat：不占用学生每日限额，不保存会话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistService {

    private final AiGatewayService aiGatewayService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 内容类型 → 中文名 */
    private String typeName(String type) {
        return switch (type == null ? "" : type) {
            case "activity" -> "校园活动";
            case "idle" -> "闲置交易";
            case "lostfound" -> "失物招领";
            case "post" -> "校园动态";
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的内容类型: " + type);
        };
    }

    /** 生成发布草稿：返回 {title, content, tags[]} */
    public Map<String, Object> compose(Long userId, AssistComposeDTO dto) {
        if (!aiGatewayService.isAiConfigured()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED,
                    "AI 服务暂不可用（未配置 API Key），可稍后重试");
        }
        String name = typeName(dto.getType());
        String extra = StrUtil.isBlank(dto.getExtra()) ? "" : "\n补充信息：" + dto.getExtra();
        String question = "请为「" + name + "」撰写一条发布文案。\n主题/需求：" + dto.getTopic() + extra
                + "\n\n要求：只返回一个 JSON 对象，不要包含任何其他文字或代码块标记，格式："
                + "{\"title\":\"吸引人的标题（不超过20字）\",\"content\":\"完整正文（80-200字，具体、自然、有吸引力）\",\"tags\":[\"#标签1\",\"#标签2\",\"#标签3\"]}";

        String raw = aiGatewayService.internalChat(userId, Constants.SCENE_ASSIST_COMPOSE, question, null);
        return parseCompose(raw);
    }

    /** 润色 / 扩写 / 精简：返回优化后文本 */
    public String polish(Long userId, AssistPolishDTO dto) {
        if (!aiGatewayService.isAiConfigured()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED,
                    "AI 服务暂不可用（未配置 API Key），可稍后重试");
        }
        String name = typeName(dto.getType());
        String actionDesc = switch (dto.getAction() == null ? "polish" : dto.getAction()) {
            case "expand" -> "扩写，补充更多细节和吸引力，保持原意与事实不变";
            case "shorten" -> "精简，保留核心信息，更简洁有力";
            default -> "润色，使表达更清晰、更吸引人，保持原意与事实不变";
        };
        String question = "请" + actionDesc + "以下「" + name + "」文案。\n只返回润色后的文本，不要解释：\n" + dto.getContent();
        return aiGatewayService.internalChat(userId, Constants.SCENE_ASSIST_POLISH, question, null).trim();
    }

    /** 容错解析生成结果（模型可能带 ```json 围栏或多余文字） */
    private Map<String, Object> parseCompose(String raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (StrUtil.isBlank(raw)) {
            throw new BizException(ResultCode.AI_INVOKE_FAIL, "AI 没有生成内容，请重试");
        }
        String text = raw.trim();
        // 去掉 ```json ... ``` 围栏
        int fenceStart = text.indexOf("```");
        if (fenceStart >= 0) {
            int brace = text.indexOf('{', fenceStart);
            int fenceEnd = text.lastIndexOf("```");
            int braceEnd = text.lastIndexOf('}');
            if (brace >= 0 && braceEnd > brace) {
                text = text.substring(brace, braceEnd + 1);
            }
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            result.put("title", node.path("title").asText("").trim());
            result.put("content", node.path("content").asText("").trim());
            List<String> tags = new ArrayList<>();
            JsonNode tagArr = node.path("tags");
            if (tagArr.isArray()) {
                for (JsonNode t : tagArr) {
                    String s = t.asText("").trim();
                    if (StrUtil.isNotBlank(s)) {
                        tags.add(s.startsWith("#") ? s : "#" + s);
                    }
                }
            }
            result.put("tags", tags);
            if (StrUtil.isBlank((String) result.get("title")) && StrUtil.isBlank((String) result.get("content"))) {
                throw new BizException(ResultCode.AI_INVOKE_FAIL, "AI 生成内容解析失败，请重试");
            }
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 辅助生成 JSON 解析失败，原文: {}", raw);
            throw new BizException(ResultCode.AI_INVOKE_FAIL, "AI 返回格式异常，请重试");
        }
    }
}
