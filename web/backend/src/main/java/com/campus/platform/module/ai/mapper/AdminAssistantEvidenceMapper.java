package com.campus.platform.module.ai.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminAssistantEvidenceMapper {
    String IDS = """
             WHERE id IN
             <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
             </script>
            """;
    String KEYWORD = """
            <if test="term != null">
                AND (title LIKE #{term} ESCAPE '!' OR description LIKE #{term} ESCAPE '!')
            </if>
            """;
    String PENDING_ORDER = " ORDER BY COALESCE(ai_risk_level,0) DESC,id LIMIT 6 </script>";

    @Select("<script> SELECT id FROM idle_item WHERE audit_status=#{pending} " + KEYWORD + PENDING_ORDER)
    List<Long> findPendingIdle(@Param("pending") int pending, @Param("term") String term);

    @Select("<script> SELECT id FROM activity WHERE audit_status=#{pending} "
            + "<if test=\"id != null\">AND id=#{id}</if>" + KEYWORD + PENDING_ORDER)
    List<Long> findPendingActivity(@Param("pending") int pending, @Param("id") Long id, @Param("term") String term);

    @Select("""
            <script>
            SELECT id,title,LEFT(description,480) AS excerpt,audit_status AS status,
                   ai_risk_level AS risk_level,ai_audit_reason AS risk_reason FROM idle_item
            """ + IDS)
    List<IdleRow> findIdle(@Param("ids") List<Long> ids);

    @Select("""
            <script>
            SELECT id,title,LEFT(description,480) AS excerpt,audit_status AS status,
                   ai_risk_level AS risk_level,ai_audit_reason AS risk_reason,
                   location,start_time,end_time,max_members,signup_deadline,
            """ + AdminAssistantReviewMapper.ACTIVITY_VERSION + " AS version FROM activity" + IDS)
    List<ActivityRow> findActivity(@Param("ids") List<Long> ids);

    @Select("<script> SELECT id,status,target_type,target_id,reason FROM report" + IDS)
    List<ReportRow> findReports(@Param("ids") List<Long> ids);

    @Select("<script> SELECT id,title,LEFT(description,480) AS excerpt FROM idle_item" + IDS)
    List<TargetRow> findIdleTargets(@Param("ids") List<Long> ids);

    @Select("<script> SELECT id,title,LEFT(description,480) AS excerpt FROM activity" + IDS)
    List<TargetRow> findActivityTargets(@Param("ids") List<Long> ids);

    @Select("<script> SELECT id,title,LEFT(description,480) AS excerpt FROM lost_found" + IDS)
    List<TargetRow> findLostTargets(@Param("ids") List<Long> ids);

    @Select("<script> SELECT id,'校园动态' AS title,LEFT(content,480) AS excerpt FROM post" + IDS)
    List<TargetRow> findPostTargets(@Param("ids") List<Long> ids);

    @Select("<script> SELECT id,'动态评论' AS title,LEFT(content,480) AS excerpt FROM post_comment" + IDS)
    List<TargetRow> findCommentTargets(@Param("ids") List<Long> ids);

    @Select("<script> SELECT id,'学生账号' AS title,NULL AS excerpt FROM user" + IDS)
    List<TargetRow> findUserTargets(@Param("ids") List<Long> ids);

    record IdleRow(long id, String title, String excerpt, int status, Integer riskLevel, String riskReason) {}

    record ActivityRow(long id, String title, String excerpt, int status, Integer riskLevel, String riskReason,
                       String location, LocalDateTime startTime, LocalDateTime endTime, Integer maxMembers,
                       LocalDateTime signupDeadline, String version) {}

    record ReportRow(long id, int status, String targetType, long targetId, String reason) {}

    record TargetRow(long id, String title, String excerpt) {}
}
