package com.campus.platform.module.activity.service;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityScheduleService {
    static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final String MY_ACTIVITIES = """
            SELECT a.id, a.title, a.location, a.start_time, a.end_time, m.status
            FROM activity_member m JOIN activity a ON a.id = m.activity_id
            WHERE m.user_id = ? AND m.status IN (?, ?)
                AND a.audit_status = ? AND a.status IN (?, ?)
            """;
    private static final RowMapper<Entry> ENTRY = (rs, row) -> new Entry(
            rs.getLong("id"), rs.getString("title"), rs.getString("location"),
            rs.getObject("start_time", LocalDateTime.class), rs.getObject("end_time", LocalDateTime.class),
            rs.getInt("status"));

    private final JdbcTemplate jdbc;
    private final ActivityMapper activities;

    public Schedule schedule(Long userId) {
        LocalDateTime now = LocalDateTime.now(CAMPUS_ZONE);
        LocalDate today = now.toLocalDate();
        LocalDate through = today.plusDays(6);
        var entries = jdbc.query(MY_ACTIVITIES + """
                AND (a.start_time IS NULL OR a.start_time < ?)
                AND (a.end_time > ? OR (a.end_time IS NULL AND (a.start_time IS NULL OR a.start_time >= ?)))
                ORDER BY a.start_time IS NULL, a.start_time, a.id
                """, ENTRY, userId, Constants.MEMBER_PENDING, Constants.MEMBER_APPROVED,
                Constants.AUDIT_PASS, Constants.ACTIVITY_SIGNING, Constants.ACTIVITY_FULL,
                through.plusDays(1).atStartOfDay(), now, now);
        return new Schedule(today, through, entries);
    }

    public ConflictCheck conflicts(Long userId, Long activityId) {
        Activity target = activities.selectById(activityId);
        if (target == null || !Integer.valueOf(Constants.AUDIT_PASS).equals(target.getAuditStatus())
                || Integer.valueOf(Constants.ACTIVITY_OFF).equals(target.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不可查看");
        }
        LocalDateTime start = target.getStartTime();
        LocalDateTime end = target.getEndTime();
        if (start == null || end == null || !start.isBefore(end)) {
            return new ConflictCheck(false, List.of());
        }

        var entries = jdbc.query(MY_ACTIVITIES + """
                AND a.id <> ? AND a.start_time < ? AND a.end_time > ? AND a.start_time < a.end_time
                ORDER BY a.start_time, a.id
                """, ENTRY, userId, Constants.MEMBER_APPROVED, Constants.MEMBER_APPROVED,
                Constants.AUDIT_PASS, Constants.ACTIVITY_SIGNING, Constants.ACTIVITY_FULL, activityId, end, start);
        List<Conflict> conflicts = new ArrayList<>();
        for (Entry entry : entries) {
            LocalDateTime overlapStart = entry.startTime().isAfter(start) ? entry.startTime() : start;
            LocalDateTime overlapEnd = entry.endTime().isBefore(end) ? entry.endTime() : end;
            long seconds = Duration.between(overlapStart, overlapEnd).getSeconds();
            conflicts.add(new Conflict(entry.activityId(), entry.title(), entry.startTime(), entry.endTime(), seconds));
        }
        return new ConflictCheck(true, conflicts);
    }

    public record Schedule(LocalDate from, LocalDate through, List<Entry> entries) {}
    public record Entry(long activityId, String title, String location, LocalDateTime startTime,
                        LocalDateTime endTime, int signupStatus) {}
    public record ConflictCheck(boolean timeKnown, List<Conflict> conflicts) {}
    public record Conflict(long activityId, String title, LocalDateTime startTime,
                           LocalDateTime endTime, long overlapSeconds) {}
}
