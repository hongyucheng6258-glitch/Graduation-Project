package com.campus.platform.module.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AgentCampusDraftMapper {
    String DRAFT_QUERY = """
            SELECT id, kind, form, target_id, target_title, submitted_id,
                   CASE WHEN submitted_id IS NOT NULL THEN 'SUBMITTED'
                        WHEN expires_at <= NOW() THEN 'EXPIRED' ELSE 'DRAFT' END AS state,
                   CASE kind
                       WHEN 'lost' THEN (SELECT audit_status FROM lost_found WHERE id=agent_campus_draft.submitted_id)
                       WHEN 'partner' THEN (SELECT audit_status FROM study_partner WHERE id=agent_campus_draft.submitted_id)
                   END AS audit_status
            FROM agent_campus_draft WHERE id=#{id} AND user_id=#{userId}
            """;

    @Select(DRAFT_QUERY)
    DraftRow findDraft(@Param("id") String id, @Param("userId") Long userId);

    @Select(DRAFT_QUERY + " FOR UPDATE")
    DraftRow lockDraft(@Param("id") String id, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO agent_campus_draft(id,user_id,kind,form,target_id,target_title,expires_at)
            VALUES(#{id},#{userId},#{kind},#{form},#{targetId,jdbcType=BIGINT},#{targetTitle,jdbcType=VARCHAR},
                   DATE_ADD(NOW(), INTERVAL 1 DAY))
            """)
    int insertDraft(@Param("id") String id, @Param("userId") Long userId, @Param("kind") String kind,
                    @Param("form") String form, @Param("targetId") Long targetId, @Param("targetTitle") String targetTitle);

    @Update("UPDATE agent_campus_draft SET form=#{form},submitted_id=#{submittedId} WHERE id=#{id}")
    int markSubmitted(@Param("id") String id, @Param("form") String form, @Param("submittedId") Long submittedId);

    record DraftRow(String id, String kind, String form, Long targetId, String targetTitle,
                    Long submittedId, String state, Integer auditStatus) {}
}
