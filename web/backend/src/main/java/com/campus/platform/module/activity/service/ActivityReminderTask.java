package com.campus.platform.module.activity.service;

import com.campus.platform.common.Constants;
import com.campus.platform.module.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityReminderTask {
    private static final DateTimeFormatter START_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm");
    private static final String ELIGIBLE = """
            FROM activity a JOIN activity_member m ON m.activity_id = a.id
            WHERE a.audit_status = ? AND a.status IN (?, ?) AND m.status = ?
                AND a.start_time > ? AND a.start_time <= ?
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final MessageService messages;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void sendUpcomingReminders() {
        LocalDateTime now = LocalDateTime.now(ActivityScheduleService.CAMPUS_ZONE);
        var candidates = jdbc.query("SELECT m.id, a.start_time " + ELIGIBLE + """
                AND NOT EXISTS (SELECT 1 FROM activity_reminder r
                    WHERE r.member_id = m.id AND r.start_time = a.start_time)
                ORDER BY a.start_time, m.id LIMIT 200
                """, (rs, index) -> new Candidate(rs.getLong(1), rs.getObject(2, LocalDateTime.class)),
                Constants.AUDIT_PASS, Constants.ACTIVITY_SIGNING, Constants.ACTIVITY_FULL,
                Constants.MEMBER_APPROVED, now, now.plusHours(1));
        for (Candidate candidate : candidates) {
            try {
                transactions.executeWithoutResult(status -> sendIfEligible(candidate));
            } catch (DuplicateKeyException ignored) {
                // Another scheduler has already handled this registration and start time.
            } catch (DataAccessException error) {
                log.warn("活动提醒发送失败，报名记录 {}", candidate.memberId(), error);
            }
        }
    }

    private void sendIfEligible(Candidate candidate) {
        LocalDateTime now = LocalDateTime.now(ActivityScheduleService.CAMPUS_ZONE);
        var current = jdbc.query("SELECT a.id, m.user_id, a.title, a.location " + ELIGIBLE + """
                AND m.id = ? AND a.start_time = ? FOR UPDATE
                """, (rs, index) -> new Reminder(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4)),
                Constants.AUDIT_PASS, Constants.ACTIVITY_SIGNING, Constants.ACTIVITY_FULL,
                Constants.MEMBER_APPROVED, now, now.plusHours(1), candidate.memberId(), candidate.startTime());
        if (current.isEmpty()) {
            return;
        }
        Reminder reminder = current.get(0);
        jdbc.update("INSERT INTO activity_reminder (member_id, start_time) VALUES (?, ?)",
                candidate.memberId(), candidate.startTime());
        String location = reminder.location() == null || reminder.location().isBlank() ? "地点待定" : reminder.location();
        messages.send(reminder.userId(), Constants.MSG_SYSTEM, "活动即将开始",
                "「%s」将于 %s 开始，%s。".formatted(reminder.title(), candidate.startTime().format(START_FORMAT), location),
                Constants.BIZ_ACTIVITY, reminder.activityId());
    }

    private record Candidate(long memberId, LocalDateTime startTime) {}
    private record Reminder(long activityId, long userId, String title, String location) {}
}
