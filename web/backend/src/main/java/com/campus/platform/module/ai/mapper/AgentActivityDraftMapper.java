package com.campus.platform.module.ai.mapper;

import com.campus.platform.module.activity.dto.ActivityPublishDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface AgentActivityDraftMapper {
    String DRAFT_QUERY = """
            SELECT d.id,d.title,d.description,d.category,d.location,d.start_time,d.end_time,d.signup_deadline,
                   d.max_members,d.expires_at,d.activity_id,d.confirmation_hash,a.audit_status,a.audit_reason,
                   CASE WHEN d.activity_id IS NOT NULL THEN 'SUBMITTED'
                        WHEN d.expires_at<=NOW() THEN 'EXPIRED' ELSE 'DRAFT' END AS state
            FROM agent_activity_draft d LEFT JOIN activity a ON a.id=d.activity_id AND a.user_id=d.user_id
            WHERE d.id=#{id} AND d.user_id=#{userId}
            """;

    @Select(DRAFT_QUERY)
    DraftRow findDraft(@Param("id") String id, @Param("userId") Long userId);

    @Select(DRAFT_QUERY + " FOR UPDATE OF d")
    DraftRow lockDraft(@Param("id") String id, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO agent_activity_draft(id,user_id,title,description,category,location,start_time,end_time,
                                             signup_deadline,max_members,expires_at)
            VALUES (#{id},#{userId},#{form.title},#{form.description},#{form.category},#{form.location},
                    #{form.startTime},#{form.endTime},#{form.signupDeadline},#{form.maxMembers},
                    DATE_ADD(NOW(), INTERVAL 1 DAY))
            """)
    int insertDraft(@Param("id") String id, @Param("userId") Long userId, @Param("form") ActivityPublishDTO form);

    @Update("""
            UPDATE agent_activity_draft SET activity_id=#{activityId},title=#{form.title},
                description=#{form.description},category=#{form.category},location=#{form.location},
                start_time=#{form.startTime},end_time=#{form.endTime},signup_deadline=#{form.signupDeadline},
                max_members=#{form.maxMembers},confirmation_hash=#{hash}
            WHERE id=#{id} AND user_id=#{userId}
            """)
    int markSubmitted(@Param("id") String id, @Param("userId") Long userId, @Param("activityId") Long activityId,
                      @Param("hash") String hash, @Param("form") ActivityPublishDTO form);

    @Select("""
            SELECT EXISTS(SELECT 1 FROM upload_resource WHERE owner_user_id=#{userId} AND resource_type='image'
                          AND CAST(resource_url AS BINARY)=CAST(#{url} AS BINARY))
            """)
    boolean ownsImage(@Param("userId") Long userId, @Param("url") String url);

    record DraftRow(String id, String title, String description, String category, String location,
                    LocalDateTime startTime, LocalDateTime endTime, LocalDateTime signupDeadline,
                    Integer maxMembers, LocalDateTime expiresAt, Long activityId, String confirmationHash,
                    Integer auditStatus, String auditReason, String state) {}
}
