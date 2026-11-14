package com.campus.platform.module.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface AgentIdleAppointmentMapper {
    String OFFER_QUERY = """
            SELECT title,SHA2(JSON_ARRAY(user_id,title,description,expect_item,category,images),256) AS version
            FROM idle_item WHERE id=#{id} AND user_id<>#{userId} AND audit_status=#{auditStatus} AND status=#{status}
            """;

    String CARD_QUERY = """
            SELECT a.id,a.item_id,a.item_title,a.remark,a.expires_at,a.appointment_id,
                   CASE WHEN a.appointment_id IS NOT NULL THEN 'SUBMITTED'
                        WHEN a.expires_at<=NOW() THEN 'EXPIRED' ELSE 'PENDING' END AS state,
                   (SELECT status FROM idle_appointment WHERE id=a.appointment_id AND buyer_id=a.user_id) AS appointment_status,
                   a.offer_version
            FROM agent_idle_appointment a WHERE a.id=#{id} AND a.user_id=#{userId}
            """;

    @Select(OFFER_QUERY)
    Offer findOffer(@Param("id") long id, @Param("userId") Long userId,
                    @Param("auditStatus") int auditStatus, @Param("status") int status);

    @Select(OFFER_QUERY + " FOR UPDATE")
    Offer lockOffer(@Param("id") long id, @Param("userId") Long userId,
                    @Param("auditStatus") int auditStatus, @Param("status") int status);

    @Select(CARD_QUERY)
    CardRow findCard(@Param("id") String id, @Param("userId") Long userId);

    @Select(CARD_QUERY + " FOR UPDATE")
    CardRow lockCard(@Param("id") String id, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO agent_idle_appointment(id,user_id,item_id,item_title,remark,expires_at,offer_version)
            VALUES(#{id},#{userId},#{itemId},#{title},#{remark},DATE_ADD(NOW(), INTERVAL 10 MINUTE),#{version})
            """)
    int insertCard(@Param("id") String id, @Param("userId") Long userId, @Param("itemId") long itemId,
                   @Param("title") String title, @Param("remark") String remark, @Param("version") String version);

    @Update("UPDATE agent_idle_appointment SET appointment_id=#{appointmentId},remark=#{remark} WHERE id=#{id}")
    int markSubmitted(@Param("id") String id, @Param("appointmentId") Long appointmentId, @Param("remark") String remark);

    record Offer(String title, String version) {}

    record CardRow(String id, long itemId, String itemTitle, String remark, LocalDateTime expiresAt,
                   Long appointmentId, String state, Integer appointmentStatus, String offerVersion) {}
}
