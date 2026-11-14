package com.campus.platform.module.idle.mapper;

import com.campus.platform.module.idle.service.IdleHandoffService.Plan;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface IdleHandoffMapper {
    @Select("""
            SELECT version, proposed_by, meeting_at, location, confirmed, buyer_done, seller_done
            FROM idle_handoff WHERE appointment_id = #{appointmentId}
            """)
    Plan findPlan(@Param("appointmentId") long appointmentId);

    @Insert("INSERT INTO idle_handoff(appointment_id) VALUES(#{appointmentId})")
    int insertPlan(@Param("appointmentId") long appointmentId);

    @Update("""
            UPDATE idle_handoff SET version=version+1, proposed_by=#{userId}, meeting_at=#{meetingAt},
                location=#{location}, confirmed=0, reminder_sent=0
            WHERE appointment_id=#{appointmentId}
            """)
    int propose(@Param("userId") long userId, @Param("meetingAt") LocalDateTime meetingAt,
                @Param("location") String location, @Param("appointmentId") long appointmentId);

    @Update("UPDATE idle_handoff SET confirmed=1 WHERE appointment_id=#{appointmentId}")
    int confirm(@Param("appointmentId") long appointmentId);

    @Update("UPDATE idle_handoff SET buyer_done=1 WHERE appointment_id=#{appointmentId}")
    int markBuyerDone(@Param("appointmentId") long appointmentId);

    @Update("UPDATE idle_handoff SET seller_done=1 WHERE appointment_id=#{appointmentId}")
    int markSellerDone(@Param("appointmentId") long appointmentId);

    @Select("""
            SELECT h.appointment_id FROM idle_handoff h JOIN idle_appointment a ON a.id=h.appointment_id
            WHERE h.confirmed=1 AND h.reminder_sent=0 AND h.meeting_at>#{now} AND h.meeting_at<=#{until} AND a.status=1
            ORDER BY h.meeting_at,h.appointment_id LIMIT 100
            """)
    List<Long> findUpcoming(@Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    @Select("""
            SELECT h.appointment_id FROM idle_handoff h JOIN idle_appointment a ON a.id=h.appointment_id
            WHERE a.status=1 AND h.confirmed=1 AND h.meeting_at<=#{cutoff} AND h.buyer_done=0 AND h.seller_done=0
            ORDER BY h.meeting_at,h.appointment_id LIMIT 100
            """)
    List<Long> findExpired(@Param("cutoff") LocalDateTime cutoff);

    @Update("""
            UPDATE idle_handoff SET reminder_sent=1 WHERE appointment_id=#{appointmentId} AND confirmed=1
            AND reminder_sent=0 AND meeting_at>#{now} AND meeting_at<=#{until}
            """)
    int markReminded(@Param("appointmentId") long appointmentId, @Param("now") LocalDateTime now,
                     @Param("until") LocalDateTime until);
}
