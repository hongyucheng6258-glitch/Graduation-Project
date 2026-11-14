package com.campus.platform.module.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminAssistantReviewMapper {
    String ACTIVITY_VERSION = """
            SHA2(JSON_ARRAY(user_id,title,description,images,category,location,start_time,end_time,
                signup_deadline,max_members,status,audit_status,ai_risk_level,ai_audit_reason,audit_revision),256)
            """;
    String IDLE_SNAPSHOT = """
            SELECT title,LEFT(description,480) AS excerpt,audit_status,
                SHA2(JSON_ARRAY(user_id,title,description,images,category,expect_item,status,
                    audit_status,ai_risk_level,ai_audit_reason,audit_revision),256) AS version
            FROM idle_item WHERE id=#{id}
            """;
    String ACTIVITY_SNAPSHOT = "SELECT title,LEFT(description,480) AS excerpt,audit_status,"
            + ACTIVITY_VERSION + " AS version FROM activity WHERE id=#{id}";
    String CARDS = """
            SELECT id,item_id,item_title AS title,item_excerpt AS excerpt,item_version AS version,
                decision,reason,expires_at,applied_at,audit_status,
                CASE WHEN applied_at IS NOT NULL THEN 'APPLIED'
                    WHEN expires_at<=NOW(6) THEN 'EXPIRED' ELSE 'PENDING' END AS state,item_kind AS kind
            FROM admin_assistant_review
            """;
    String OWNED_CARD = CARDS + " WHERE id=#{id} AND admin_id=#{adminId}";

    @Select(IDLE_SNAPSHOT)
    Snapshot findIdle(@Param("id") long id);

    @Select(IDLE_SNAPSHOT + " FOR UPDATE")
    Snapshot lockIdle(@Param("id") long id);

    @Select(ACTIVITY_SNAPSHOT)
    Snapshot findActivity(@Param("id") long id);

    @Select(ACTIVITY_SNAPSHOT + " FOR UPDATE")
    Snapshot lockActivity(@Param("id") long id);

    @Select("SELECT id FROM admin WHERE id=#{adminId} AND status=0 AND role IN ('audit','super') FOR SHARE")
    Long lockReviewer(@Param("adminId") Long adminId);

    @Select(OWNED_CARD)
    ReviewRow findReview(@Param("id") String id, @Param("adminId") Long adminId);

    @Select(OWNED_CARD + " FOR UPDATE")
    ReviewRow lockReview(@Param("id") String id, @Param("adminId") Long adminId);

    @Select(CARDS + " WHERE admin_id=#{adminId} AND session_id=#{sessionId} ORDER BY expires_at DESC,id DESC LIMIT 50")
    List<ReviewRow> findSessionReviews(@Param("adminId") Long adminId, @Param("sessionId") String sessionId);

    @Insert("""
            INSERT INTO admin_assistant_review(id,session_id,admin_id,item_kind,item_id,item_title,item_excerpt,
                item_version,decision,reason,expires_at)
            VALUES(#{id},#{sessionId},#{adminId},#{kind},#{itemId},#{item.title},#{item.excerpt},#{item.version},
                #{decision},#{reason},DATE_ADD(NOW(6),INTERVAL 10 MINUTE))
            """)
    int insertReview(@Param("id") String id, @Param("sessionId") String sessionId, @Param("adminId") Long adminId,
                     @Param("kind") String kind, @Param("itemId") long itemId, @Param("item") Snapshot item,
                     @Param("decision") String decision, @Param("reason") String reason);

    @Update("""
            UPDATE admin_assistant_review SET applied_at=NOW(6),audit_status=#{status}
            WHERE id=#{id} AND expires_at>NOW(6)
            """)
    int markApplied(@Param("id") String id, @Param("status") int status);

    record Snapshot(String title, String excerpt, int auditStatus, String version) {}
    record ReviewRow(String id, long itemId, String title, String excerpt, String version, String decision,
                     String reason, LocalDateTime expiresAt, LocalDateTime appliedAt, Integer auditStatus,
                     String state, String kind) {}
}
