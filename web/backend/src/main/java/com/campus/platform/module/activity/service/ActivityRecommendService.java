package com.campus.platform.module.activity.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.entity.ActivityMember;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.activity.mapper.ActivityMemberMapper;
import com.campus.platform.module.activity.vo.ActivityRecommendVO;
import com.campus.platform.module.ai.gateway.AiGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 活动智能推荐：结合学生报名历史偏好 + 候选活动（类别/时间/热度），
 * 由 AI 挑选最合适的活动并给出推荐理由。
 */
@Service
@RequiredArgsConstructor
public class ActivityRecommendService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private final ActivityMapper activityMapper;
    private final ActivityMemberMapper memberMapper;
    private final AiGatewayService aiGatewayService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ActivityRecommendVO> recommend(Long userId) {
        if (!aiGatewayService.isAiConfigured()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED,
                    "AI 服务暂不可用（未配置 API Key），可稍后重试");
        }
        // 1. 我的报名历史（已通过）
        List<ActivityMember> myMembers = memberMapper.selectList(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getUserId, userId)
                .eq(ActivityMember::getStatus, Constants.MEMBER_APPROVED)
                .orderByDesc(ActivityMember::getId)
                .last("LIMIT 20"));
        Set<Long> joinedIds = new HashSet<>();
        for (ActivityMember m : myMembers) {
            joinedIds.add(m.getActivityId());
        }
        StringBuilder history = new StringBuilder();
        if (joinedIds.isEmpty()) {
            history.append("（还没有报名记录，可综合活动热度与近期活动推荐）");
        } else {
            List<Activity> myActs = activityMapper.selectBatchIds(joinedIds);
            for (Activity a : myActs) {
                history.append("· ").append(nullTo(a.getTitle(), "")).append("（类别：").append(nullTo(a.getCategory(), ""))
                        .append("，时间：").append(a.getStartTime() == null ? "" : a.getStartTime().format(FMT)).append("）\n");
            }
        }
        // 2. 候选活动（报名中/已满员且未结束，排除我报过的）
        LocalDateTime now = LocalDateTime.now();
        List<Activity> candidates = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getAuditStatus, Constants.AUDIT_PASS)
                .and(w -> w.eq(Activity::getStatus, Constants.ACTIVITY_SIGNING)
                        .or().eq(Activity::getStatus, Constants.ACTIVITY_FULL))
                .and(w -> w.isNull(Activity::getEndTime)
                        .or().gt(Activity::getEndTime, now))
                .notIn(!joinedIds.isEmpty(), Activity::getId, joinedIds)
                .last("LIMIT 40"));
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        StringBuilder sb = new StringBuilder();
        Map<Long, Long> counts = new HashMap<>();
        for (Activity a : candidates) {
            Long cnt = memberMapper.selectCount(new LambdaQueryWrapper<ActivityMember>()
                    .eq(ActivityMember::getActivityId, a.getId())
                    .eq(ActivityMember::getStatus, Constants.MEMBER_APPROVED));
            counts.put(a.getId(), cnt);
            sb.append("[id=").append(a.getId()).append("] 标题：").append(nullTo(a.getTitle(), ""))
                    .append(" | 类别：").append(nullTo(a.getCategory(), ""))
                    .append(" | 时间：").append(a.getStartTime() == null ? "" : a.getStartTime().format(FMT))
                    .append(" | 地点：").append(nullTo(a.getLocation(), ""))
                    .append(" | 已报名：").append(cnt).append("人")
                    .append("\n");
        }
        // 3. AI 推荐
        Map<String, String> params = new HashMap<>();
        params.put("my_history", history.toString());
        params.put("candidates", sb.toString());
        String raw = aiGatewayService.internalChat(userId, Constants.SCENE_ACTIVITY_RECOMMEND,
                "请为我推荐合适的活动", params);
        // 4. 解析
        List<Long> ids = new ArrayList<>();
        Map<Long, String> reasons = new LinkedHashMap<>();
        if (!parseRecommends(raw, ids, reasons)) {
            return Collections.emptyList();
        }
        Map<Long, Activity> byId = new HashMap<>();
        for (Activity a : candidates) {
            byId.put(a.getId(), a);
        }
        List<ActivityRecommendVO> result = new ArrayList<>();
        for (Long id : ids) {
            if (result.size() >= 5) break;
            Activity a = byId.get(id);
            if (a == null) continue;
            ActivityRecommendVO vo = new ActivityRecommendVO();
            vo.setId(a.getId());
            vo.setTitle(a.getTitle());
            vo.setCategory(a.getCategory());
            vo.setLocation(a.getLocation());
            vo.setStartTime(a.getStartTime());
            vo.setEndTime(a.getEndTime());
            vo.setMemberCount(counts.getOrDefault(id, 0L));
            vo.setReason(reasons.get(id));
            result.add(vo);
        }
        return result;
    }

    private boolean parseRecommends(String raw, List<Long> ids, Map<Long, String> reasons) {
        if (StrUtil.isBlank(raw)) return false;
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
            JsonNode arr = node.path("recommends");
            if (!arr.isArray()) return false;
            for (JsonNode m : arr) {
                long id = m.path("id").asLong(0);
                if (id <= 0) continue;
                ids.add(id);
                reasons.put(id, m.path("reason").asText(""));
            }
            return !ids.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private String nullTo(String v, String dft) {
        return v == null || v.isBlank() ? dft : v;
    }
}
