package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.service.ActivityService;
import com.campus.platform.module.activity.vo.ActivityVO;
import com.campus.platform.module.ai.service.CampusGuideService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ActivityAgentTools implements AgentToolSource {
    private static final int LIMIT = 8;

    private final ActivityService activities;
    private final ObjectMapper json;
    private final CampusGuideService legacyGuide;

    @Override
    public List<AgentTool> tools() {
        return List.of(
                new AgentTool("get_my_signups", "查询当前登录学生的最近报名记录与审核状态，不需要任何参数。",
                        Map.of(), List.of(), this::mySignups),
                new AgentTool("search_activities", "查询可见校园活动。可选 keyword，startDate、endDate 为本地 YYYY-MM-DD，包含首尾日期。",
                        Map.of("keyword", "string", "startDate", "string", "endDate", "string"), List.of(), this::search),
                new AgentTool("get_activity_detail", "按活动 ID 查询实时详情、名额和当前登录用户的报名状态。",
                        Map.of("activityId", "integer"), List.of("activityId"), this::detail),
                new AgentTool("get_campus_updates", "查询最新校园公告或动态。category 取 notice 或 post。",
                        Map.of("category", "string"), List.of("category"), this::updates)
        );
    }

    private JsonNode updates(Long uid, JsonNode args) {
        String category = args.path("category").asText();
        if (!List.of("notice", "post").contains(category)) {
            throw new BizException(ResultCode.BAD_REQUEST, "未知校园数据类别");
        }
        return json.createObjectNode().put("updates", abbreviate(legacyGuide.publicUpdates(category), 3600));
    }

    private JsonNode mySignups(Long userId, JsonNode args) {
        var page = activities.mySignups(userId, 1, LIMIT);
        ObjectNode result = json.createObjectNode().put("total", page.getTotal());
        var items = result.putArray("items");
        page.getList().forEach(row -> items.addObject().put("activityId", row.getActivityId())
                .put("title", row.getActivityTitle()).put("signupStatus", signupStatus(row.getStatus())));
        return result;
    }

    private JsonNode search(Long userId, JsonNode args) {
        String keyword = args.path("keyword").asText("").strip();
        if (keyword.length() > 80) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动关键词过长");
        }
        LocalDate start = date(args, "startDate");
        LocalDate end = date(args, "endDate");
        if (start != null && end != null && end.isBefore(start)) {
            throw new BizException(ResultCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        var page = activities.list(keyword, null, 1, LIMIT, start == null ? null : start.atStartOfDay(),
                end == null ? null : end.plusDays(1).atStartOfDay());
        ObjectNode result = json.createObjectNode().put("total", page.getTotal());
        var items = result.putArray("items");
        page.getList().forEach(activity -> items.add(activityFields(activity)));
        return result;
    }

    private JsonNode detail(Long userId, JsonNode args) {
        long id = args.path("activityId").asLong();
        if (id <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动 ID 无效");
        }
        var activity = activities.detail(id, userId);
        if (!Objects.equals(activity.getAuditStatus(), Constants.AUDIT_PASS)) {
            throw new BizException(ResultCode.NOT_FOUND, "未找到可查看的活动");
        }
        return activityFields(activity)
                .put("description", abbreviate(activity.getDescription(), 600))
                .put("mySignupStatus", signupStatus(activity.getMySignupStatus()));
    }

    private ObjectNode activityFields(ActivityVO activity) {
        return json.createObjectNode()
                .put("activityId", activity.getId()).put("title", activity.getTitle())
                .put("startTime", Objects.toString(activity.getStartTime(), null))
                .put("endTime", Objects.toString(activity.getEndTime(), null))
                .put("location", activity.getLocation()).put("status", activity.getDisplayStatusText())
                .put("canSignup", activity.getCanSignup()).put("memberCount", activity.getMemberCount())
                .put("maxMembers", activity.getMaxMembers());
    }

    private LocalDate date(JsonNode args, String name) {
        if (!args.has(name)) {
            return null;
        }
        try {
            String value = args.path(name).asText();
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw new IllegalArgumentException();
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "日期须为 YYYY-MM-DD");
        }
    }

    private String signupStatus(Integer status) {
        if (status == null) {
            return "未报名";
        }
        return switch (status) {
            case Constants.MEMBER_PENDING -> "待审核";
            case Constants.MEMBER_APPROVED -> "已通过";
            case Constants.MEMBER_REJECTED -> "未通过";
            default -> "未知状态";
        };
    }

    private String abbreviate(String value, int limit) {
        return value != null && value.length() > limit ? value.substring(0, limit) : value;
    }
}
