package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.dto.SignupDTO;
import com.campus.platform.module.activity.service.ActivityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivitySignupActions implements AgentToolSource {
    private final ActivityService activities;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Override
    public List<AgentTool> tools() {
        return List.of(new AgentTool("prepare_activity_signup",
                "学生明确表示要报名时，按 activityId 创建一张确认卡。此工具不会提交报名。",
                Map.of("activityId", "integer", "remark", "string"), List.of("activityId"), this::prepare));
    }

    private JsonNode prepare(Long uid, JsonNode args) {
        long id = args.path("activityId").asLong();
        String remark = args.path("remark").asText("").strip();
        if (uid == null || id <= 0 || remark.length() > 200) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动 ID 或报名备注不合法");
        }
        var activity = activities.detail(id, uid);
        if (Boolean.TRUE.equals(activity.getIsOwner())) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能报名自己发布的活动");
        }
        if (activity.getMySignupStatus() != null) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该活动已有报名记录");
        }
        if (!Boolean.TRUE.equals(activity.getCanSignup())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该活动当前无法报名");
        }
        String actionId = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(10).withNano(0);
        jdbc.update("""
                INSERT INTO agent_action(id,user_id,activity_id,activity_title,remark,expires_at)
                VALUES(?,?,?,?,?,?)
                """, actionId, uid, id, activity.getTitle(), remark, expiry);
        return json.valueToTree(new Proposal(actionId, id, activity.getTitle(), remark, expiry));
    }

    @Transactional
    public Confirmation confirm(Long uid, String id) {
        return confirm(uid, id, null);
    }

    @Transactional
    public Confirmation confirm(Long uid, String id, String remark) {
        if (remark != null && remark.length() > 255) {
            throw new BizException(ResultCode.BAD_REQUEST, "备注最多 255 字");
        }
        if (uid == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (id == null || !id.matches("[a-f0-9-]{36}")) {
            throw new BizException(ResultCode.NOT_FOUND, "未找到报名确认卡");
        }
        var row = jdbc.query("""
                SELECT activity_id,activity_title,remark,state,expires_at>NOW()
                FROM agent_action WHERE id=? AND user_id=? FOR UPDATE
                """, (rs, index) -> new Action(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getBoolean(5)), id, uid).stream().findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "未找到报名确认卡"));
        if ("SUBMITTED".equals(row.state())) {
            return new Confirmation(row.activityId(), row.title(), "SUBMITTED", row.remark());
        }
        if (!row.current()) {
            throw new BizException(ResultCode.BAD_REQUEST, "确认卡已过期，请重新请求报名");
        }

        var dto = new SignupDTO();
        dto.setRemark(remark == null ? row.remark() : remark.strip());
        activities.signup(uid, row.activityId(), dto);
        jdbc.update("UPDATE agent_action SET state='SUBMITTED',confirmed_at=NOW(),remark=? WHERE id=?", dto.getRemark(), id);
        return new Confirmation(row.activityId(), row.title(), "SUBMITTED", dto.getRemark());
    }

    Card card(Long uid, String id) {
        return jdbc.query("""
                SELECT id,activity_id,activity_title,remark,expires_at,
                    CASE WHEN state='PENDING' AND expires_at<=NOW() THEN 'EXPIRED' ELSE state END
                FROM agent_action WHERE id=? AND user_id=?
                """, (rs, row) -> new Card(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, LocalDateTime.class), rs.getString(6)), id, uid).stream().findFirst().orElse(null);
    }

    private record Action(long activityId, String title, String remark, String state, boolean current) {}
    public record Proposal(String id, long activityId, String activityTitle, String remark, LocalDateTime expiresAt) {}
    public record Confirmation(long activityId, String activityTitle, String status, String remark) {}
    public record Card(String id, long activityId, String activityTitle, String remark, LocalDateTime expiresAt, String result) {}
}
