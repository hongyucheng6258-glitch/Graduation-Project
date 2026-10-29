package com.campus.platform.module.ai.service;

import com.campus.platform.module.ai.entity.AiAuditResult;

import cn.hutool.core.util.StrUtil;
import com.campus.platform.module.ai.gateway.AiConfigHolder;
import com.campus.platform.module.ai.gateway.AiGatewayService;
import com.campus.platform.common.Constants;
import com.campus.platform.config.SystemConfigHolder;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.post.entity.Post;
import com.campus.platform.module.partner.entity.StudyPartner;
import com.campus.platform.module.partner.mapper.StudyPartnerMapper;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.post.mapper.PostMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 学生内容AI分级审核：低风险自动通过，中高风险保留人工审核，AI故障时安全降级为人工审核。
 */
@Slf4j
@Service
public class ContentAiAuditService {

    private final LostFoundMapper lostFoundMapper;
    private final IdleItemMapper idleItemMapper;
    private final ActivityMapper activityMapper;
    private final PostMapper postMapper;
    private final StudyPartnerMapper studyPartnerMapper;
    private final AiGatewayService aiGatewayService;
    private final AiConfigHolder aiConfigHolder;
    private final SystemConfigHolder systemConfigHolder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContentAiAuditService(LostFoundMapper lostFoundMapper,
                                 IdleItemMapper idleItemMapper,
                                 ActivityMapper activityMapper,
                                 PostMapper postMapper,
                                 StudyPartnerMapper studyPartnerMapper,
                                 AiGatewayService aiGatewayService,
                                 AiConfigHolder aiConfigHolder,
                                 SystemConfigHolder systemConfigHolder) {
        this.lostFoundMapper = lostFoundMapper;
        this.idleItemMapper = idleItemMapper;
        this.activityMapper = activityMapper;
        this.postMapper = postMapper;
        this.studyPartnerMapper = studyPartnerMapper;
        this.aiGatewayService = aiGatewayService;
        this.aiConfigHolder = aiConfigHolder;
        this.systemConfigHolder = systemConfigHolder;
    }

    public void audit(String type, Object content, Long userId, String title, String body) {
        AiAuditResult result = ruleDecision(title, body);
        if (aiEnabled()) {
            try {
                result = parse(aiGatewayService.internalChat(userId, Constants.SCENE_CONTENT_AUDIT,
                        buildAuditText(type, title, body), Map.of("contentType", type)));
            } catch (Exception e) {
                log.warn("AI内容审核失败，回退本地规则: type={}, userId={}", type, userId, e);
                // AI 不可用时回退本地规则（安全底线）：规则判 LOW 的内容直接放行，
                // 避免动态/闲置/活动等永久停留在「待审核」；命中规则词的保持拦截（HIGH 拒绝 / MEDIUM 人工）。
                result = "LOW".equals(result.level())
                        ? result
                        : new AiAuditResult("MEDIUM", 50, "AI审核暂不可用，已转人工审核", List.of("AI降级"));
            }
        }
        applyDecision(type, content, result);
    }

    public void applyDecision(String type, Object content, AiAuditResult result) {
        int riskLevel = riskLevel(result.level());
        int score = result.score() == null ? riskLevel * 40 + 10 : Math.max(0, Math.min(100, result.score()));
        String reason = StrUtil.blankToDefault(result.reason(), "未发现明显风险");
        LocalDateTime now = LocalDateTime.now();
        boolean autoPass = riskLevel == 0;

        switch (type) {
            case Constants.BIZ_IDLE -> {
                IdleItem item = (IdleItem) content;
                fill(item, riskLevel, reason, now, autoPass);
                idleItemMapper.updateById(item);
            }
            case Constants.BIZ_ACTIVITY -> {
                Activity activity = (Activity) content;
                fill(activity, riskLevel, reason, now, autoPass);
                activityMapper.updateById(activity);
            }
            case Constants.BIZ_LOSTFOUND -> {
                LostFound lf = (LostFound) content;
                fill(lf, riskLevel, reason, now, autoPass);
                lostFoundMapper.updateById(lf);
            }
            case Constants.BIZ_POST -> {
                Post post = (Post) content;
                fill(post, riskLevel, reason, now, autoPass);
                postMapper.updateById(post);
            }
            case Constants.BIZ_PARTNER -> {
                StudyPartner partner = (StudyPartner) content;
                fill(partner, riskLevel, reason, now, autoPass);
                studyPartnerMapper.updateById(partner);
            }
            default -> throw new IllegalArgumentException("不支持的内容类型: " + type);
        }
        log.info("内容审核完成: type={}, score={}, level={}, autoPass={}", type, score, result.level(), autoPass);
    }

    private AiAuditResult ruleDecision(String title, String body) {
        String text = StrUtil.nullToEmpty(title) + " " + StrUtil.nullToEmpty(body);
        // 高/中风险词从系统配置读取（管理端可在线修改，即时生效）
        List<String> highRiskWords = systemConfigHolder.getAuditHighRiskWords();
        for (String word : highRiskWords) {
            if (text.contains(word)) {
                return new AiAuditResult("HIGH", 85, "命中高风险规则：" + word, List.of("交易风险"));
            }
        }
        List<String> mediumRiskWords = systemConfigHolder.getAuditMediumRiskWords();
        for (String word : mediumRiskWords) {
            if (text.contains(word)) {
                return new AiAuditResult("MEDIUM", 50, "命中需复核规则：" + word, List.of("人工复核"));
            }
        }
        return new AiAuditResult("LOW", 10, "普通校园内容，规则预审通过", List.of());
    }

    private boolean aiEnabled() {
        if (aiConfigHolder == null || aiGatewayService == null) return false;
        // AI 审核开关从系统配置读取（管理端可在线切换）
        if (!systemConfigHolder.isAiAuditEnabled()) return false;
        String key = aiConfigHolder.getApiKey();
        return StrUtil.isNotBlank(key) && !key.contains("xxx");
    }

    private String buildAuditText(String type, String title, String body) {
        return "内容类型：" + type + "\n标题：" + StrUtil.nullToEmpty(title)
                + "\n正文：" + StrUtil.nullToEmpty(body)
                + "\n请仅返回JSON：{\"level\":\"LOW|MEDIUM|HIGH\",\"score\":0-100,\"reason\":\"原因\",\"categories\":[]}";
    }

    private AiAuditResult parse(String response) throws Exception {
        String json = response == null ? "" : response.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        JsonNode root = objectMapper.readTree(json);
        String level = root.path("level").asText("MEDIUM").toUpperCase();
        if (!List.of("LOW", "MEDIUM", "HIGH").contains(level)) level = "MEDIUM";
        int score = root.path("score").asInt(level.equals("LOW") ? 10 : level.equals("HIGH") ? 85 : 50);
        String reason = root.path("reason").asText("AI建议人工复核");
        return new AiAuditResult(level, score, reason, List.of());
    }

    private int riskLevel(String level) {
        if ("LOW".equalsIgnoreCase(level)) return 0;
        if ("HIGH".equalsIgnoreCase(level)) return 2;
        return 1;
    }

    private void fill(IdleItem item, int risk, String reason, LocalDateTime time, boolean pass) {
        item.setAiRiskLevel(risk); item.setAiAuditReason(reason); item.setAiAuditTime(time); item.setAuditSource("ai");
        if (pass && item.getAuditStatus() == Constants.AUDIT_PENDING) item.setAuditStatus(Constants.AUDIT_PASS);
    }
    private void fill(Activity item, int risk, String reason, LocalDateTime time, boolean pass) {
        item.setAiRiskLevel(risk); item.setAiAuditReason(reason); item.setAiAuditTime(time); item.setAuditSource("ai");
        if (pass && item.getAuditStatus() == Constants.AUDIT_PENDING) item.setAuditStatus(Constants.AUDIT_PASS);
    }
    private void fill(LostFound item, int risk, String reason, LocalDateTime time, boolean pass) {
        item.setAiRiskLevel(risk); item.setAiAuditReason(reason); item.setAiAuditTime(time); item.setAuditSource("ai");
        if (pass && item.getAuditStatus() == Constants.AUDIT_PENDING) item.setAuditStatus(Constants.AUDIT_PASS);
    }
    private void fill(Post item, int risk, String reason, LocalDateTime time, boolean pass) {
        item.setAiRiskLevel(risk); item.setAiAuditReason(reason); item.setAiAuditTime(time); item.setAuditSource("ai");
        if (pass && item.getAuditStatus() == Constants.AUDIT_PENDING) item.setAuditStatus(Constants.AUDIT_PASS);
    }
    private void fill(StudyPartner item, int risk, String reason, LocalDateTime time, boolean pass) {
        item.setAiRiskLevel(risk); item.setAiAuditReason(reason); item.setAiAuditTime(time); item.setAuditSource("ai");
        if (pass && item.getAuditStatus() == Constants.AUDIT_PENDING) item.setAuditStatus(Constants.AUDIT_PASS);
    }
}
