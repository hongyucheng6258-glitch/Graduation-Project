package com.campus.platform.module.activity.service;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.activity.mapper.ActivityWaitlistMapper;
import com.campus.platform.module.activity.mapper.ActivityWaitlistMapper.Candidate;
import com.campus.platform.module.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ActivityWaitlistMapper waitlist;
    private final ActivityMapper activities;
    private final MessageService messages;
    private final TransactionTemplate transactions;

    public List<Entry> mine(long userId) {
        return waitlist.mine(userId);
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
        waitlist.deleteEntry(activityId, userId);
        waitlist.enqueue(activityId, userId, now);
        return entry(userId, activityId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void leave(long userId, long activityId, long entryId) {
        Activity activity = activities.selectForUpdate(activityId);
        int changed = waitlist.cancel(entryId, activityId, userId);
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
        int changed = waitlist.claim(entryId, activity.getId(), userId, now);
        if (changed != 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "递补邀请已失效，请刷新查看");
        }
        return true;
    }

    void release(long activityId, long userId, String state) {
        waitlist.release(state, activityId, userId);
    }

    boolean reservedForOthers(Activity activity, long userId) {
        return limit(activity) > 0 && occupied(activity.getId(), now(), userId) >= limit(activity);
    }

    boolean offersFillRemainingSeats(Activity activity, long userId, long approved) {
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
            waitlist.expireAll(id);
            return;
        }
        waitlist.expireOffers(id, now);
        long available = limit(activity) - occupied(id, now, null);
        if (available <= 0) return;
        var next = waitlist.findCandidates(id, available);
        LocalDateTime until = now.plusMinutes(30);
        for (LocalDateTime boundary : new LocalDateTime[]{activity.getSignupDeadline(), activity.getStartTime(), activity.getEndTime()}) {
            if (boundary != null && boundary.isBefore(until)) until = boundary;
        }
        for (Candidate candidate : next) {
            waitlist.offer(until, candidate.id());
            messages.send(candidate.userId(), Constants.MSG_INTERACT, "活动递补邀请",
                    "「%s」有空位，请在 %s 前到活动详情确认递补。".formatted(activity.getTitle(), until.toString().replace('T', ' ')),
                    Constants.BIZ_ACTIVITY, id);
        }
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void advanceQueues() {
        var ids = waitlist.findQueuesToAdvance(now(), now(), now(), now(), now(), now(), now());
        for (Long id : ids) {
            try {
                transactions.executeWithoutResult(status -> refresh(activities.selectForUpdate(id)));
            } catch (RuntimeException error) {
                log.warn("活动候补推进失败，活动 {}", id, error);
            }
        }
    }

    private Entry entry(long userId, long activityId) {
        return waitlist.findEntry(activityId, userId)
                .stream().findFirst().orElse(null);
    }

    private boolean hasMembership(long userId, long activityId) {
        return waitlist.countMembership(activityId, userId) > 0;
    }

    private long occupied(long activityId, LocalDateTime now, Long exceptUser) {
        long reserved = waitlist.countReserved(activityId, Constants.MEMBER_PENDING, Constants.MEMBER_APPROVED);
        return reserved + holds(activityId, now, exceptUser);
    }

    private long holds(long activityId, LocalDateTime now, Long exceptUser) {
        return waitlist.countOffers(activityId, now, exceptUser);
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

    public record Entry(long id, long activityId, String title, String state, LocalDateTime offerUntil, LocalDateTime queuedAt) {}
    public record View(boolean canJoin, Entry entry) {}
}
