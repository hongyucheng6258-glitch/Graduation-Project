package com.campus.platform.module.ai.adminassistant;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.admin.service.AuditService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class AdminAssistantReviewActions {
    private static final String IDLE_SNAPSHOT = """
            SELECT title,LEFT(description,480),audit_status,
                SHA2(JSON_ARRAY(user_id,title,description,images,category,expect_item,status,
                    audit_status,ai_risk_level,ai_audit_reason,audit_revision),256)
            FROM idle_item WHERE id=?
            """;
    private static final String ACTIVITY_SNAPSHOT = "SELECT title,LEFT(description,480),audit_status,"
            + AdminAssistantEvidence.ACTIVITY_VERSION + " FROM activity WHERE id=?";
    private static final String CARDS = """
            SELECT id,item_id,item_title,item_excerpt,item_version,decision,reason,expires_at,applied_at,audit_status,
                CASE WHEN applied_at IS NOT NULL THEN 'APPLIED'
                    WHEN expires_at<=NOW(6) THEN 'EXPIRED' ELSE 'PENDING' END,item_kind
            FROM admin_assistant_review
            """;
    private static final RowMapper<Stored> READ_CARD = (rs, row) -> new Stored(new Card(rs.getString(1), rs.getString(2),
            rs.getString(3), rs.getString(4), rs.getString(6), rs.getString(7), rs.getObject(8, LocalDateTime.class),
            rs.getObject(9, LocalDateTime.class), rs.getObject(10, Integer.class), rs.getString(11), rs.getString(12)), rs.getString(5));

    private final JdbcTemplate jdbc;
    private final AdminAssistantSessions sessions;
    private final AuditService audit;

    Card prepare(Long adminId, Request request) {
        String kind = request.kind() == null ? Constants.BIZ_IDLE : request.kind();
        long itemId;
        try {
            itemId = Long.parseLong(request.itemId());
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.BAD_REQUEST, label(kind) + "编号无效");
        }
        var reference = new AdminAssistantEvidence.Ref(kind, itemId);
        if (sessions.history(adminId, request.sessionId(), 50).stream().noneMatch(turn -> turn.refs().contains(reference))) {
            throw new BizException(ResultCode.NOT_FOUND, "请先在当前会话中查询该" + label(kind));
        }
        String reason = request.reason() == null ? "" : request.reason().strip();
        if ("REJECT".equals(request.decision()) && reason.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写驳回理由");
        }
        var item = requirePending(kind, itemId, false);
        if (Constants.BIZ_ACTIVITY.equals(kind) && !item.version().equals(request.version())) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "活动信息已变更，请重新查询并核对");
        }
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO admin_assistant_review(id,session_id,admin_id,item_kind,item_id,item_title,item_excerpt,item_version,
                    decision,reason,expires_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,DATE_ADD(NOW(6),INTERVAL 10 MINUTE))
                """, id, request.sessionId(), adminId, kind, itemId, item.title(), item.excerpt(), item.version(), request.decision(), reason);
        return find(adminId, id, false).card();
    }

    @Transactional
    public Card confirm(Long adminId, String id) {
        if (jdbc.queryForList("SELECT id FROM admin WHERE id=? AND status=0 AND role IN ('audit','super') FOR SHARE",
                Long.class, adminId).isEmpty()) {
            throw new BizException(ResultCode.FORBIDDEN, "管理员已无审核权限");
        }
        var stored = find(adminId, id, true);
        var card = stored.card();
        if ("APPLIED".equals(card.state())) return card;
        if ("EXPIRED".equals(card.state())) throw new BizException(ResultCode.BAD_REQUEST, "审核确认卡已过期");
        long itemId = Long.parseLong(card.itemId());
        var item = requirePending(card.kind(), itemId, true);
        if (!item.version().equals(stored.version())) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, label(card.kind()) + "信息已变更，请重新查询并核对");
        }
        boolean pass = "PASS".equals(card.decision());
        int status = Constants.BIZ_ACTIVITY.equals(card.kind())
                ? audit.reviewPendingActivity(itemId, pass, card.reason())
                : audit.reviewPendingIdle(itemId, pass, card.reason());
        int applied = jdbc.update("""
                UPDATE admin_assistant_review SET applied_at=NOW(6),audit_status=? WHERE id=? AND expires_at>NOW(6)
                """, status, id);
        if (applied != 1) throw new BizException(ResultCode.BAD_REQUEST, "审核确认卡已过期");
        return find(adminId, id, false).card();
    }

    List<Card> list(Long adminId, String sessionId) {
        sessions.requireOwner(adminId, sessionId);
        return jdbc.query(CARDS + " WHERE admin_id=? AND session_id=? ORDER BY expires_at DESC,id DESC LIMIT 50",
                READ_CARD, adminId, sessionId).stream().map(Stored::card).toList();
    }

    private Stored find(Long adminId, String id, boolean lock) {
        return jdbc.query(CARDS + " WHERE id=? AND admin_id=?" + (lock ? " FOR UPDATE" : ""), READ_CARD, id, adminId)
                .stream().findFirst().orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "未找到审核确认卡"));
    }

    private Snapshot requirePending(String kind, long id, boolean lock) {
        String snapshot = switch (kind) {
            case Constants.BIZ_IDLE -> IDLE_SNAPSHOT;
            case Constants.BIZ_ACTIVITY -> ACTIVITY_SNAPSHOT;
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的审核类型");
        };
        var item = jdbc.query(snapshot + (lock ? " FOR UPDATE" : ""), (rs, row) ->
                        new Snapshot(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4)), id)
                .stream().findFirst().orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, label(kind) + "已不存在"));
        if (item.auditStatus() != Constants.AUDIT_PENDING) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该" + label(kind) + "已处理，请刷新");
        }
        return item;
    }

    private String label(String kind) {
        return Constants.BIZ_ACTIVITY.equals(kind) ? "活动" : "闲置";
    }

    private record Snapshot(String title, String excerpt, int auditStatus, String version) {}
    private record Stored(Card card, String version) {}
    public record Request(@NotBlank @Pattern(regexp = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}") String sessionId,
                          @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String itemId,
                          @NotBlank @Pattern(regexp = "PASS|REJECT") String decision, @Size(max = 255) String reason,
                          @Pattern(regexp = "idle|activity") String kind,
                          @Pattern(regexp = "[0-9a-f]{64}") String version) {}
    public record Card(String id, String itemId, String title, String excerpt, String decision, String reason,
                       LocalDateTime expiresAt, LocalDateTime appliedAt, Integer auditStatus, String state, String kind) {}
}
