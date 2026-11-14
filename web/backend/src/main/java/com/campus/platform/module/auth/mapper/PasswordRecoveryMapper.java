package com.campus.platform.module.auth.mapper;

import com.campus.platform.module.auth.service.PasswordRecoveryService.Status;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface PasswordRecoveryMapper {
    @Insert("""
            INSERT INTO student_password_recovery(ticket_hash, student_no, claimed_name, contact, explanation)
            VALUES (#{ticketHash}, #{studentNo}, #{name}, #{contact}, #{explanation})
            """)
    int insertApplication(@Param("ticketHash") String ticketHash, @Param("studentNo") String studentNo,
                          @Param("name") String name, @Param("contact") String contact,
                          @Param("explanation") String explanation);

    @Select("SELECT status, decision_reason AS reason FROM student_password_recovery WHERE ticket_hash = #{ticketHash}")
    Status findStatus(@Param("ticketHash") String ticketHash);

    @Select("""
            SELECT id, student_no, claimed_name AS name, contact, explanation,
                   status, decision_reason AS reason, created_at
            FROM student_password_recovery WHERE id < #{before} ORDER BY id DESC LIMIT 30
            """)
    List<ApplicationRow> listBefore(@Param("before") long before);

    @Select("SELECT student_no FROM student_password_recovery WHERE id = #{id}")
    String findStudentNo(@Param("id") long id);

    @Update("""
            UPDATE student_password_recovery
            SET status = #{status}, decision_reason = #{reason,jdbcType=VARCHAR}, decided_at = NOW()
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int decide(@Param("id") long id, @Param("status") String status, @Param("reason") String reason);

    @Select("""
            SELECT id, student_no FROM student_password_recovery
            WHERE ticket_hash = #{ticketHash} AND status = 'APPROVED'
              AND decided_at > NOW() - INTERVAL 7 DAY FOR UPDATE
            """)
    ClaimRow lockClaimable(@Param("ticketHash") String ticketHash);

    @Update("UPDATE student_password_recovery SET status = 'CLAIMED' WHERE id = #{id}")
    int markClaimed(@Param("id") long id);

    @Select("SELECT id FROM `user` WHERE student_no = #{studentNo} AND status = 0")
    Long findActiveUserId(@Param("studentNo") String studentNo);

    record ApplicationRow(long id, String studentNo, String name, String contact, String explanation,
                          String status, String reason, LocalDateTime createdAt) {}

    record ClaimRow(long id, String studentNo) {}
}
