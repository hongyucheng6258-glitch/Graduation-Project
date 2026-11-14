package com.campus.platform.module.activity.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityReminderMapper {
    String ELIGIBLE = """
            FROM activity a JOIN activity_member m ON m.activity_id = a.id
            WHERE a.audit_status = #{auditPass} AND a.status IN (#{signing}, #{full}) AND m.status = #{approved}
                AND a.start_time > #{now} AND a.start_time <= #{until}
            """;

    @Select("SELECT m.id AS member_id, a.start_time " + ELIGIBLE + """
            AND NOT EXISTS (SELECT 1 FROM activity_reminder r
                WHERE r.member_id = m.id AND r.start_time = a.start_time)
            ORDER BY a.start_time, m.id LIMIT 200
            """)
    List<Candidate> findCandidates(@Param("auditPass") int auditPass, @Param("signing") int signing,
                                   @Param("full") int full, @Param("approved") int approved,
                                   @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    @Select("SELECT a.id AS activity_id, m.user_id, a.title, a.location " + ELIGIBLE + """
            AND m.id = #{memberId} AND a.start_time = #{startTime} FOR UPDATE
            """)
    Reminder lockEligible(@Param("auditPass") int auditPass, @Param("signing") int signing,
                          @Param("full") int full, @Param("approved") int approved,
                          @Param("now") LocalDateTime now, @Param("until") LocalDateTime until,
                          @Param("memberId") long memberId, @Param("startTime") LocalDateTime startTime);

    @Insert("INSERT INTO activity_reminder (member_id, start_time) VALUES (#{memberId}, #{startTime})")
    int insertReminder(@Param("memberId") long memberId, @Param("startTime") LocalDateTime startTime);

    record Candidate(long memberId, LocalDateTime startTime) {}
    record Reminder(long activityId, long userId, String title, String location) {}
}
