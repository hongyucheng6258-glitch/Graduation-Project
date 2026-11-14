package com.campus.platform.module.activity.mapper;

import com.campus.platform.module.activity.service.ActivityScheduleService.Entry;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityScheduleMapper {
    String SCHEDULE_QUERY = """
            SELECT a.id AS activity_id, a.title, a.location, a.start_time, a.end_time,
                   m.status AS signup_status
            FROM activity_member m JOIN activity a ON a.id = m.activity_id
            WHERE m.user_id = #{userId}
                AND a.audit_status = #{auditPass} AND a.status IN (#{signing}, #{full})
            """;

    @Select(SCHEDULE_QUERY + """
            AND m.status IN (#{pending}, #{approved})
            AND (a.start_time IS NULL OR a.start_time < #{through})
            AND (a.end_time > #{now} OR (a.end_time IS NULL AND (a.start_time IS NULL OR a.start_time >= #{now})))
            ORDER BY a.start_time IS NULL, a.start_time, a.id
            """)
    List<Entry> findUpcoming(@Param("userId") Long userId, @Param("pending") int pending,
                             @Param("approved") int approved, @Param("auditPass") int auditPass,
                             @Param("signing") int signing, @Param("full") int full,
                             @Param("through") LocalDateTime through, @Param("now") LocalDateTime now);

    @Select(SCHEDULE_QUERY + """
            AND m.status = #{approved}
            AND a.id <> #{activityId} AND a.start_time < #{end} AND a.end_time > #{start}
            AND a.start_time < a.end_time
            ORDER BY a.start_time, a.id
            """)
    List<Entry> findOverlaps(@Param("userId") Long userId, @Param("approved") int approved,
                             @Param("auditPass") int auditPass, @Param("signing") int signing,
                             @Param("full") int full, @Param("activityId") Long activityId,
                             @Param("end") LocalDateTime end, @Param("start") LocalDateTime start);
}
