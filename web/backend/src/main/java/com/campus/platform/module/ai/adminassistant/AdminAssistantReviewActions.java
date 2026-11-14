package com.campus.platform.module.ai.adminassistant;

import com.campus.platform.module.admin.audit.AdminOperationTarget;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.admin.service.AuditService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.campus.platform.module.ai.mapper.AdminAssistantReviewMapper;
import com.campus.platform.module.ai.mapper.AdminAssistantReviewMapper.ReviewRow;
import com.campus.platform.module.ai.mapper.AdminAssistantReviewMapper.Snapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class AdminAssistantReviewActions {
    private final AdminAssistantReviewMapper reviews;
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
        reviews.insertReview(id, request.sessionId(), adminId, kind, itemId, item, request.decision(), reason);
        return find(adminId, id, false).card();
    }

    @Transactional
    public Card confirm(Long adminId, String id) {
        if (reviews.lockReviewer(adminId) == null) {
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
        int applied = reviews.markApplied(id, status);
        if (applied != 1) throw new BizException(ResultCode.BAD_REQUEST, "审核确认卡已过期");
        return find(adminId, id, false).card();
    }

    List<Card> list(Long adminId, String sessionId) {
        sessions.requireOwner(adminId, sessionId);
        return reviews.findSessionReviews(adminId, sessionId).stream().map(this::toStored).map(Stored::card).toList();
    }

    private Stored find(Long adminId, String id, boolean lock) {
        var row = lock ? reviews.lockReview(id, adminId) : reviews.findReview(id, adminId);
        if (row == null) {
            throw new BizException(ResultCode.NOT_FOUND, "未找到审核确认卡");
        }
        return toStored(row);
    }

    private Stored toStored(ReviewRow row) {
        return new Stored(new Card(row.id(), Long.toString(row.itemId()), row.title(), row.excerpt(),
                row.decision(), row.reason(), row.expiresAt(), row.appliedAt(), row.auditStatus(), row.state(),
                row.kind()), row.version());
    }

    private Snapshot requirePending(String kind, long id, boolean lock) {
        Snapshot item = switch (kind) {
            case Constants.BIZ_IDLE -> lock ? reviews.lockIdle(id) : reviews.findIdle(id);
            case Constants.BIZ_ACTIVITY -> lock ? reviews.lockActivity(id) : reviews.findActivity(id);
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的审核类型");
        };
        if (item == null) {
            throw new BizException(ResultCode.NOT_FOUND, label(kind) + "已不存在");
        }
        if (item.auditStatus() != Constants.AUDIT_PENDING) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该" + label(kind) + "已处理，请刷新");
        }
        return item;
    }

    private String label(String kind) {
        return Constants.BIZ_ACTIVITY.equals(kind) ? "活动" : "闲置";
    }

    private record Stored(Card card, String version) {}
    public record Request(@NotBlank @Pattern(regexp = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}") String sessionId,
                          @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String itemId,
                          @NotBlank @Pattern(regexp = "PASS|REJECT") String decision, @Size(max = 255) String reason,
                          @Pattern(regexp = "idle|activity") String kind,
                          @Pattern(regexp = "[0-9a-f]{64}") String version) {}
    public record Card(String id, String itemId, String title, String excerpt, String decision, String reason,
                       LocalDateTime expiresAt, LocalDateTime appliedAt, Integer auditStatus, String state, String kind) implements AdminOperationTarget {
        @Override
        public String operationTargetId() { return itemId; }

        @Override
        public String operationTargetType() { return kind; }

        @Override
        public String operationDecision() { return decision; }
    }
}
