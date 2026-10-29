package com.campus.platform.module.idle.service;

import cn.hutool.core.util.StrUtil;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.AiGatewayService;
import com.campus.platform.module.idle.vo.IdleEstimateVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 闲置 AI 智能估价：根据物品名称/描述/分类/期望换物，由 AI 给出
 * 校园二手参考价区间、定价建议与发布卖点文案。
 */
@Service
@RequiredArgsConstructor
public class IdleEstimateService {

    private static final Logger log = LoggerFactory.getLogger(IdleEstimateService.class);

    private final AiGatewayService aiGatewayService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IdleEstimateVO estimate(Long userId, String title, String description, String category, String expectItem) {
        if (!aiGatewayService.isAiConfigured()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED,
                    "AI 服务暂不可用（未配置 API Key），可稍后重试");
        }
        Map<String, String> params = new HashMap<>();
        params.put("item_title", nullTo(title, ""));
        params.put("item_desc", nullTo(description, ""));
        params.put("category", nullTo(category, ""));
        params.put("expect_item", nullTo(expectItem, ""));
        String raw = aiGatewayService.internalChat(userId, Constants.SCENE_IDLE_ESTIMATE, "请进行闲置估价", params);
        return parseEstimate(raw);
    }

    /** 容错解析 AI 输出的估价 JSON */
    private IdleEstimateVO parseEstimate(String raw) {
        IdleEstimateVO vo = new IdleEstimateVO();
        if (StrUtil.isBlank(raw)) {
            throw new BizException(ResultCode.AI_INVOKE_FAIL, "AI 没有返回估价结果，请重试");
        }
        String text = raw.trim();
        int fenceStart = text.indexOf("```");
        if (fenceStart >= 0) {
            int brace = text.indexOf('{', fenceStart);
            int braceEnd = text.lastIndexOf('}');
            if (brace >= 0 && braceEnd > brace) {
                text = text.substring(brace, braceEnd + 1);
            }
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            vo.setPriceMin(node.path("priceMin").asInt(0));
            vo.setPriceMax(node.path("priceMax").asInt(0));
            vo.setReference(node.path("reference").asText("").trim());
            vo.setTip(node.path("tip").asText("").trim());
            List<String> points = new ArrayList<>();
            JsonNode arr = node.path("sellingPoints");
            if (arr.isArray()) {
                for (JsonNode p : arr) {
                    String s = p.asText("").trim();
                    if (StrUtil.isNotBlank(s)) {
                        points.add(s);
                    }
                }
            }
            vo.setSellingPoints(points);
            if (vo.getPriceMin() == null || vo.getPriceMin() <= 0 || vo.getPriceMax() == null || vo.getPriceMax() < vo.getPriceMin()) {
                throw new BizException(ResultCode.AI_INVOKE_FAIL, "AI 估价结果异常，请重试");
            }
            return vo;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("闲置AI估价 JSON 解析失败，原文: {}", raw);
            throw new BizException(ResultCode.AI_INVOKE_FAIL, "AI 估价结果解析失败，请重试");
        }
    }

    private String nullTo(String v, String dft) {
        return v == null || v.isBlank() ? dft : v;
    }
}
