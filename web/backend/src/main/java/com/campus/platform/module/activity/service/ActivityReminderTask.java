package com.campus.platform.module.activity.service;

import com.campus.platform.common.Constants;
import com.campus.platform.module.activity.mapper.ActivityReminderMapper;
import com.campus.platform.module.activity.mapper.ActivityReminderMapper.Candidate;
import com.campus.platform.module.activity.mapper.ActivityReminderMapper.Reminder;
import com.campus.platform.module.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
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

    private final ActivityReminderMapper reminders;
    private final TransactionTemplate transactions;
    private final MessageService messages;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void sendUpcomingReminders() {
        LocalDateTime now = LocalDateTime.now(ActivityScheduleService.CAMPUS_ZONE);
        var candidates = reminders.findCandidates(Constants.AUDIT_PASS, Constants.ACTIVITY_SIGNING,
                Constants.ACTIVITY_FULL, Constants.MEMBER_APPROVED, now, now.plusHours(1));
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
        Reminder reminder = reminders.lockEligible(Constants.AUDIT_PASS, Constants.ACTIVITY_SIGNING,
                Constants.ACTIVITY_FULL, Constants.MEMBER_APPROVED, now, now.plusHours(1),
                candidate.memberId(), candidate.startTime());
        if (reminder == null) {
            return;
        }
        reminders.insertReminder(candidate.memberId(), candidate.startTime());
        String location = reminder.location() == null || reminder.location().isBlank() ? "地点待定" : reminder.location();
        messages.send(reminder.userId(), Constants.MSG_SYSTEM, "活动即将开始",
                "「%s」将于 %s 开始，%s。".formatted(reminder.title(), candidate.startTime().format(START_FORMAT), location),
                Constants.BIZ_ACTIVITY, reminder.activityId());
    }
}
