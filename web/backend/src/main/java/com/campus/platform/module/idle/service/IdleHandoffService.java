package com.campus.platform.module.idle.service;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.idle.entity.IdleAppointment;
import com.campus.platform.module.idle.mapper.IdleAppointmentMapper;
import com.campus.platform.module.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdleHandoffService {
    private static final String SELECT = """
            SELECT version,proposed_by,meeting_at,location,confirmed,buyer_done,seller_done
            FROM idle_handoff WHERE appointment_id=?
            """;
    private static final RowMapper<Plan> PLAN = (rs, index) -> new Plan(rs.getInt(1), rs.getObject(2, Long.class),
            rs.getObject(3, LocalDateTime.class), rs.getString(4), rs.getBoolean(5), rs.getBoolean(6), rs.getBoolean(7));
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private final JdbcTemplate jdbc;
    private final IdleAppointmentMapper appointments;
    private final com.campus.platform.module.idle.mapper.IdleItemMapper idleItems;
    private final MessageService messages;
    private final TransactionTemplate transactions;

    void register(long appointmentId) {
        jdbc.update("INSERT INTO idle_handoff(appointment_id) VALUES(?)", appointmentId);
    }

    public Plan view(long userId, long appointmentId) {
        requireParticipant(appointments.selectById(appointmentId), userId);
        return plan(appointmentId);
    }

    @Transactional
    public Plan propose(long userId, long appointmentId, int version, LocalDateTime meetingAt, String location) {
        IdleAppointment appointment = accepted(appointmentId, userId);
        Plan plan = plan(appointmentId);
        if (plan != null && (plan.buyerDone() || plan.sellerDone())) {
            throw invalid("一方已确认交接，无法改约");
        }
        if ((plan == null ? 0 : plan.version()) != version) {
            throw invalid("交接安排已更新，请刷新后重试");
        }
        if (!meetingAt.isAfter(now())) {
            throw invalid("交接时间必须晚于现在");
        }
        if (plan == null) register(appointmentId);
        jdbc.update("""
                UPDATE idle_handoff SET version=version+1,proposed_by=?,meeting_at=?,location=?,confirmed=0,reminder_sent=0
                WHERE appointment_id=?
                """, userId, meetingAt, location.strip(), appointmentId);
        notify(appointment, other(appointment, userId), "待确认交接安排",
                "%s 在%s交接，请确认或提出其他安排。".formatted(meetingAt.format(DISPLAY_TIME), location.strip()));
        return plan(appointmentId);
    }

    @Transactional
    public Plan confirm(long userId, long appointmentId, int version) {
        IdleAppointment appointment = accepted(appointmentId, userId);
        Plan plan = plan(appointmentId);
        if (plan == null || plan.meetingAt() == null || plan.version() != version
                || Objects.equals(plan.proposedBy(), userId)) {
            throw invalid("请确认对方最新提出的交接安排");
        }
        if (!plan.confirmed()) {
            if (!plan.meetingAt().isAfter(now())) throw invalid("交接时间已过，请重新安排");
            jdbc.update("UPDATE idle_handoff SET confirmed=1 WHERE appointment_id=?", appointmentId);
            notify(appointment, plan.proposedBy(), "交接安排已确认",
                    "%s 在%s交接。".formatted(plan.meetingAt().format(DISPLAY_TIME), plan.location()));
        }
        return plan(appointmentId);
    }

    boolean hasCompletionConfirmation(long appointmentId) {
        Plan current = plan(appointmentId);
        return current != null && (current.buyerDone() || current.sellerDone());
    }

    /** Called with the appointment locked. A legacy appointment can still use its original completion flow. */
    public boolean markDone(IdleAppointment appointment, long userId) {
        Plan plan = plan(appointment.getId());
        if (plan == null) return true;
        if (!plan.confirmed() || plan.meetingAt() == null || now().isBefore(plan.meetingAt())) {
            throw invalid("请先确认交接安排，并在约定时间到达后确认完成");
        }
        boolean buyer = Objects.equals(appointment.getBuyerId(), userId);
        boolean alreadyDone = buyer ? plan.buyerDone() : plan.sellerDone();
        boolean otherDone = buyer ? plan.sellerDone() : plan.buyerDone();
        if (!alreadyDone) {
            jdbc.update(buyer ? "UPDATE idle_handoff SET buyer_done=1 WHERE appointment_id=?"
                    : "UPDATE idle_handoff SET seller_done=1 WHERE appointment_id=?", appointment.getId());
            if (!otherDone) notify(appointment, other(appointment, userId), "对方已确认交接",
                    "请在实际完成互换后确认交接，双方确认后可评价。");
        }
        return otherDone;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void remind() {
        var now = now();
        var ids = jdbc.queryForList("""
                SELECT h.appointment_id FROM idle_handoff h JOIN idle_appointment a ON a.id=h.appointment_id
                WHERE h.confirmed=1 AND h.reminder_sent=0 AND h.meeting_at>? AND h.meeting_at<=? AND a.status=1
                ORDER BY h.meeting_at,h.appointment_id LIMIT 100
                """, Long.class, now, now.plusHours(1));
        for (Long id : ids) {
            try {
                transactions.executeWithoutResult(status -> remindOne(id));
            } catch (RuntimeException error) {
                log.warn("闲置交接提醒失败，预约 {}", id, error);
            }
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expireMissedHandoffs() {
        var cutoff = now().minusHours(24);
        var ids = jdbc.queryForList("""
                SELECT h.appointment_id FROM idle_handoff h JOIN idle_appointment a ON a.id=h.appointment_id
                WHERE a.status=1 AND h.confirmed=1 AND h.meeting_at<=? AND h.buyer_done=0 AND h.seller_done=0
                ORDER BY h.meeting_at,h.appointment_id LIMIT 100
                """, Long.class, cutoff);
        for (Long id : ids) {
            try {
                transactions.executeWithoutResult(status -> expireOne(id, cutoff));
            } catch (RuntimeException error) {
                log.warn("闲置交接超时处理失败，预约 {}", id, error);
            }
        }
    }

    private void expireOne(long id, LocalDateTime cutoff) {
        IdleAppointment appointment = appointments.selectForUpdate(id);
        if (appointment == null || !Objects.equals(appointment.getStatus(), Constants.APPOINT_ACCEPTED)) return;
        Plan current = plan(id);
        if (current == null || !current.confirmed() || current.meetingAt() == null
                || current.meetingAt().isAfter(cutoff) || current.buyerDone() || current.sellerDone()) return;

        appointment.setStatus(Constants.APPOINT_CANCELLED);
        appointments.updateById(appointment);
        idleItems.releaseReservation(appointment.getItemId());
        String title = "闲置交接超时，预约已取消";
        String body = "约定时间已过去 24 小时，双方均未确认完成。系统已取消预约。";
        notify(appointment, appointment.getBuyerId(), title, body);
        notify(appointment, appointment.getSellerId(), title, body);
    }

    private void remindOne(long id) {
        IdleAppointment appointment = appointments.selectForUpdate(id);
        if (appointment == null || !Objects.equals(appointment.getStatus(), Constants.APPOINT_ACCEPTED)) return;
        var now = now();
        int changed = jdbc.update("""
                UPDATE idle_handoff SET reminder_sent=1 WHERE appointment_id=? AND confirmed=1
                AND reminder_sent=0 AND meeting_at>? AND meeting_at<=?
                """, id, now, now.plusHours(1));
        if (changed == 1) {
            Plan plan = plan(id);
            String body = "%s 在%s交接。".formatted(plan.meetingAt().format(DISPLAY_TIME), plan.location());
            notify(appointment, appointment.getBuyerId(), "闲置交接即将开始", body);
            notify(appointment, appointment.getSellerId(), "闲置交接即将开始", body);
        }
    }

    private IdleAppointment accepted(long id, long userId) {
        IdleAppointment appointment = appointments.selectForUpdate(id);
        requireParticipant(appointment, userId);
        if (!Objects.equals(appointment.getStatus(), Constants.APPOINT_ACCEPTED)) throw invalid("预约未处于已接受状态");
        return appointment;
    }

    private static void requireParticipant(IdleAppointment appointment, long userId) {
        if (appointment == null) throw new BizException(ResultCode.NOT_FOUND, "预约不存在");
        if (!Objects.equals(appointment.getBuyerId(), userId) && !Objects.equals(appointment.getSellerId(), userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作该预约");
        }
    }

    private Plan plan(long id) {
        return jdbc.query(SELECT, PLAN, id).stream().findFirst().orElse(null);
    }

    private void notify(IdleAppointment appointment, long userId, String title, String body) {
        String type = Objects.equals(appointment.getBuyerId(), userId) ? "handoff_buyer" : "handoff_seller";
        messages.send(userId, Constants.MSG_INTERACT, title, body, type, appointment.getId());
    }

    private static long other(IdleAppointment appointment, long userId) {
        return Objects.equals(appointment.getBuyerId(), userId) ? appointment.getSellerId() : appointment.getBuyerId();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }

    private static BizException invalid(String reason) {
        return new BizException(ResultCode.BAD_REQUEST, reason);
    }

    public record Plan(int version, Long proposedBy, LocalDateTime meetingAt, String location,
                       boolean confirmed, boolean buyerDone, boolean sellerDone) {}
}
