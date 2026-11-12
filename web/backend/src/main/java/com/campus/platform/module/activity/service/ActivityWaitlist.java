package com.campus.platform.module.activity.service;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityWaitlist {
    private static final String FIELDS = "w.id,w.activity_id,a.title,w.state,w.offer_until,w.queued_at";
    private static final RowMapper<Entry> ROW = (rs, index) -> new Entry(rs.getLong(1), rs.getLong(2), rs.getString(3),
            rs.getString(4), rs.getObject(5, LocalDateTime.class), rs.getObject(6, LocalDateTime.class));
    private static final String SELECT = "SELECT " + FIELDS + " FROM activity_waitlist w JOIN activity a ON a.id=w.activity_id";

    private final JdbcTemplate jdbc;
    private final ActivityMapper activities;
    private final MessageService messages;
    private final TransactionTemplate transactions;

    public List<Entry> mine(long userId) {
        return jdbc.query(SELECT + " WHERE w.user_id=? ORDER BY w.id DESC LIMIT 100", ROW, userId);
    }

    public View view(long userId, long activityId) {
        Activity activity = activities.selectById(activityId);
        Entry entry = entry(userId, activityId);
        LocalDateTime now = now();
        boolean available = open(activity, now) && limit(activity) > 0
                && !Objects.equals(activity.getUserId(), userId) && !hasMembership(userId, activityId)
                && (entry == null || !active(entry.state()))
                && occupied(activityId, now, null) >= limit(activity);
        return new View(available, entry);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Entry join(long userId, long activityId) {
        Activity activity = activities.selectForUpdate(activityId);
        LocalDateTime now = now();
        if (!open(activity, now) || limit(activity) == 0 || Objects.equals(activity.getUserId(), userId)) {
            throw unavailable();
        }
        refresh(activity);
        Entry existing = entry(userId, activityId);
        if (hasMembership(userId, activityId)) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该活动已有报名记录");
        }
        if (existing != null && active(existing.state())) {
            return existing;
        }
        if (occupied(activityId, now, null) < limit(activity)) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动仍有空位，请直接报名");
        }
        jdbc.update("DELETE FROM activity_waitlist WHERE activity_id=? AND user_id=?", activityId, userId);
        jdbc.update("INSERT INTO activity_waitlist(activity_id,user_id,state,queued_at) VALUES(?,?,'WAITING',?)",
                activityId, userId, now);
        return entry(userId, activityId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void leave(long userId, long activityId, long entryId) {
        Activity activity = activities.selectForUpdate(activityId);
        int changed = jdbc.update("""
                UPDATE activity_waitlist SET state='CANCELLED'
                WHERE id=? AND activity_id=? AND user_id=? AND state IN ('WAITING','OFFERED')
                """, entryId, activityId, userId);
        if (changed != 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "候补状态已变化，请刷新后重试");
        }
        refresh(activity);
    }

    /** Called with the activity row locked in the caller's transaction. */
    boolean claim(Activity activity, long userId, long entryId) {
        LocalDateTime now = now();
        if (activity == null) throw unavailable();
        Entry entry = entry(userId, activity.getId());
        if (entry != null && entry.id() == entryId && List.of("CLAIMED", "APPROVED").contains(entry.state())) {
            return false;
        }
        if (!open(activity, now) || limit(activity) == 0
                || occupied(activity.getId(), now, userId) >= limit(activity)) {
            throw unavailable();
        }
        int changed = jdbc.update("""
                UPDATE activity_waitlist SET state='CLAIMED'
                WHERE id=? AND activity_id=? AND user_id=? AND state='OFFERED' AND offer_until>?
                """, entryId, activity.getId(), userId, now);
        if (changed != 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "递补邀请已失效，请刷新查看");
        }
        return true;
    }

    void release(long activityId, long userId, String state) {
        jdbc.update("""
                UPDATE activity_waitlist SET state=? WHERE activity_id=? AND user_id=?
                AND (state='CLAIMED' OR (state='APPROVED' AND ?='CANCELLED'))
                """, state, activityId, userId, state);
    }

    boolean reservedForOthers(Activity activity, long userId, long approved) {
        if (limit(activity) == 0) return false;
        return approved + holds(activity.getId(), now(), userId) >= limit(activity);
    }

    boolean queued(long activityId, long userId) {
        Entry row = entry(userId, activityId);
        return row != null && ("WAITING".equals(row.state()) || "OFFERED".equals(row.state()));
    }

    /** Expire offers and reserve any released seats in join order under the activity lock. */
    void refresh(Activity activity) {
        if (activity == null) return;
        long id = activity.getId();
        LocalDateTime now = now();
        if (!open(activity, now) || limit(activity) == 0) {
            jdbc.update("UPDATE activity_waitlist SET state='EXPIRED' WHERE activity_id=? AND state IN ('WAITING','OFFERED')", id);
            return;
        }
        jdbc.update("UPDATE activity_waitlist SET state='EXPIRED' WHERE activity_id=? AND state='OFFERED' AND offer_until<=?", id, now);
        long available = limit(activity) - occupied(id, now, null);
        if (available <= 0) return;
        var next = jdbc.query("""
                SELECT w.id,w.user_id FROM activity_waitlist w
                WHERE w.activity_id=? AND w.state='WAITING'
                  AND NOT EXISTS (SELECT 1 FROM activity_member m WHERE m.activity_id=w.activity_id AND m.user_id=w.user_id)
                ORDER BY w.id LIMIT ?
                """, (rs, row) -> new Candidate(rs.getLong(1), rs.getLong(2)), id, available);
        LocalDateTime until = now.plusMinutes(30);
        for (LocalDateTime boundary : new LocalDateTime[]{activity.getSignupDeadline(), activity.getStartTime(), activity.getEndTime()}) {
            if (boundary != null && boundary.isBefore(until)) until = boundary;
        }
        for (Candidate candidate : next) {
            jdbc.update("UPDATE activity_waitlist SET state='OFFERED',offer_until=? WHERE id=?", until, candidate.id());
            messages.send(candidate.userId(), Constants.MSG_INTERACT, "活动递补邀请",
                    "「%s」有空位，请在 %s 前到活动详情确认递补。".formatted(activity.getTitle(), until.toString().replace('T', ' ')),
                    Constants.BIZ_ACTIVITY, id);
        }
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void advanceQueues() {
        var ids = jdbc.queryForList("""
                SELECT DISTINCT w.activity_id FROM activity_waitlist w JOIN activity a ON a.id=w.activity_id
                WHERE (w.state='OFFERED' AND w.offer_until<=?)
                   OR (w.state='WAITING' AND (a.status IN (2,3) OR a.audit_status<>1 OR a.max_members=0
                      OR a.start_time<=? OR a.end_time<=? OR a.signup_deadline<=?
                      OR a.max_members > (SELECT COUNT(*) FROM activity_member m WHERE m.activity_id=a.id AND m.status=1)
                         + (SELECT COUNT(*) FROM activity_waitlist q WHERE q.activity_id=a.id AND q.state IN ('OFFERED','CLAIMED'))))
                   OR (w.state='OFFERED' AND (a.status IN (2,3) OR a.audit_status<>1 OR a.max_members=0
                      OR a.start_time<=? OR a.end_time<=? OR a.signup_deadline<=?))
                ORDER BY w.activity_id LIMIT 100
                """, Long.class, now(), now(), now(), now(), now(), now(), now());
        for (Long id : ids) {
            try {
                transactions.executeWithoutResult(status -> refresh(activities.selectForUpdate(id)));
            } catch (RuntimeException error) {
                log.warn("活动候补推进失败，活动 {}", id, error);
            }
        }
    }

    private Entry entry(long userId, long activityId) {
        return jdbc.query(SELECT + " WHERE w.activity_id=? AND w.user_id=?", ROW, activityId, userId)
                .stream().findFirst().orElse(null);
    }

    private boolean hasMembership(long userId, long activityId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM activity_member WHERE activity_id=? AND user_id=?",
                Long.class, activityId, userId) > 0;
    }

    private long occupied(long activityId, LocalDateTime now, Long exceptUser) {
        long approved = jdbc.queryForObject("SELECT COUNT(*) FROM activity_member WHERE activity_id=? AND status=?",
                Long.class, activityId, Constants.MEMBER_APPROVED);
        return approved + holds(activityId, now, exceptUser);
    }

    private long holds(long activityId, LocalDateTime now, Long exceptUser) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM activity_waitlist WHERE activity_id=?
                AND (state='CLAIMED' OR (state='OFFERED' AND offer_until>?))
                AND (? IS NULL OR user_id<>?)
                """, Long.class, activityId, now, exceptUser, exceptUser);
    }

    private static boolean active(String state) {
        return List.of("WAITING", "OFFERED", "CLAIMED").contains(state);
    }

    private static int limit(Activity activity) {
        return activity.getMaxMembers() == null ? 0 : activity.getMaxMembers();
    }

    private static boolean open(Activity activity, LocalDateTime now) {
        return activity != null && Objects.equals(activity.getAuditStatus(), Constants.AUDIT_PASS)
                && !Objects.equals(activity.getStatus(), Constants.ACTIVITY_OFF)
                && !Objects.equals(activity.getStatus(), Constants.ACTIVITY_ENDED)
                && (activity.getSignupDeadline() == null || now.isBefore(activity.getSignupDeadline()))
                && (activity.getStartTime() == null || now.isBefore(activity.getStartTime()))
                && (activity.getEndTime() == null || now.isBefore(activity.getEndTime()));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ActivityScheduleService.CAMPUS_ZONE).withNano(0);
    }

    private static BizException unavailable() {
        return new BizException(ResultCode.BAD_REQUEST, "该活动当前不可候补或递补");
    }

    private record Candidate(long id, long userId) {}
    public record Entry(long id, long activityId, String title, String state, LocalDateTime offerUntil, LocalDateTime queuedAt) {}
    public record View(boolean canJoin, Entry entry) {}
}
