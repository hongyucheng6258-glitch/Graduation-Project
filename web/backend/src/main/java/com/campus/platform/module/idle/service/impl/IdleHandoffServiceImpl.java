package com.campus.platform.module.idle.service.impl;

import com.campus.platform.module.idle.service.IIdleHandoffService;
import jakarta.annotation.Resource;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.idle.entity.IdleAppointment;
import com.campus.platform.module.idle.mapper.IdleAppointmentMapper;
import com.campus.platform.module.idle.mapper.IdleHandoffMapper;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.message.service.IMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Service
@Slf4j
public class IdleHandoffServiceImpl implements IIdleHandoffService {
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    @Resource
    private IdleHandoffMapper handoffs;
    @Resource
    private IdleAppointmentMapper appointments;
    @Resource
    private IdleItemMapper idleItems;
    @Resource
    private IMessageService messages;
    @Resource
    private TransactionTemplate transactions;

    @Override
    public void register(long appointmentId) {
        handoffs.insertPlan(appointmentId);
    }

    @Override
    public Plan view(long userId, long appointmentId) {
        requireParticipant(appointments.selectById(appointmentId), userId);
        return findPlan(appointmentId);
    }

    @Transactional
    @Override
    public Plan propose(long userId, long appointmentId, int version, LocalDateTime meetingAt, String location) {
        IdleAppointment appointment = lockAcceptedAppointment(appointmentId, userId);
        Plan plan = findPlan(appointmentId);
        if (plan != null && (plan.buyerDone() || plan.sellerDone())) {
            throw invalidHandoff("一方已确认交接，无法改约");
        }
        if ((plan == null ? 0 : plan.version()) != version) {
            throw invalidHandoff("交接安排已更新，请刷新后重试");
        }
        if (!meetingAt.isAfter(handoffNow())) {
            throw invalidHandoff("交接时间必须晚于现在");
        }
        if (plan == null) {
            register(appointmentId);
        }
        String meetingLocation = location.strip();
        handoffs.propose(userId, meetingAt, meetingLocation, appointmentId);
        sendHandoffNotice(appointment, otherParticipantId(appointment, userId), "待确认交接安排",
                "%s 在%s交接，请确认或提出其他安排。".formatted(meetingAt.format(DISPLAY_TIME), meetingLocation));
        return findPlan(appointmentId);
    }

    @Transactional
    @Override
    public Plan confirm(long userId, long appointmentId, int version) {
        IdleAppointment appointment = lockAcceptedAppointment(appointmentId, userId);
        Plan plan = findPlan(appointmentId);
        if (plan == null || plan.meetingAt() == null || plan.version() != version
                || Objects.equals(plan.proposedBy(), userId)) {
            throw invalidHandoff("请确认对方最新提出的交接安排");
        }
        if (!plan.confirmed()) {
            if (!plan.meetingAt().isAfter(handoffNow())) throw invalidHandoff("交接时间已过，请重新安排");
            handoffs.confirm(appointmentId);
            sendHandoffNotice(appointment, plan.proposedBy(), "交接安排已确认",
                    "%s 在%s交接。".formatted(plan.meetingAt().format(DISPLAY_TIME), plan.location()));
        }
        return findPlan(appointmentId);
    }

    @Override
    public boolean hasCompletionConfirmation(long appointmentId) {
        Plan current = findPlan(appointmentId);
        return current != null && (current.buyerDone() || current.sellerDone());
    }

    /** 调用方已锁定预约。没有交接安排的旧预约可直接完成。 */
    @Override
    public boolean markDone(IdleAppointment appointment, long userId) {
        Plan plan = findPlan(appointment.getId());
        if (plan == null) return true;
        if (!plan.confirmed() || plan.meetingAt() == null || handoffNow().isBefore(plan.meetingAt())) {
            throw invalidHandoff("请先确认交接安排，并在约定时间到达后确认完成");
        }
        boolean buyer = Objects.equals(appointment.getBuyerId(), userId);
        boolean alreadyDone = buyer ? plan.buyerDone() : plan.sellerDone();
        boolean otherDone = buyer ? plan.sellerDone() : plan.buyerDone();
        if (!alreadyDone) {
            if (buyer) {
                handoffs.markBuyerDone(appointment.getId());
            } else {
                handoffs.markSellerDone(appointment.getId());
            }
            if (!otherDone) sendHandoffNotice(appointment, otherParticipantId(appointment, userId), "对方已确认交接",
                    "请在实际完成互换后确认交接，双方确认后可评价。");
        }
        return otherDone;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Override
    public void remind() {
        LocalDateTime now = handoffNow();
        for (long id : handoffs.findUpcoming(now, now.plusHours(1))) {
            try {
                transactions.executeWithoutResult(status -> remindOne(id));
            } catch (RuntimeException error) {
                log.warn("闲置交接提醒失败，预约 {}", id, error);
            }
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Override
    public void expireMissedHandoffs() {
        LocalDateTime cutoff = handoffNow().minusHours(24);
        for (long id : handoffs.findExpired(cutoff)) {
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
        Plan current = findPlan(id);
        if (current == null || !current.confirmed() || current.meetingAt() == null
                || current.meetingAt().isAfter(cutoff) || current.buyerDone() || current.sellerDone()) return;

        appointment.setStatus(Constants.APPOINT_CANCELLED);
        appointments.updateById(appointment);
        idleItems.releaseReservation(appointment.getItemId());
        String title = "闲置交接超时，预约已取消";
        String body = "约定时间已过去 24 小时，双方均未确认完成。系统已取消预约。";
        sendHandoffNotice(appointment, appointment.getBuyerId(), title, body);
        sendHandoffNotice(appointment, appointment.getSellerId(), title, body);
    }

    private void remindOne(long id) {
        IdleAppointment appointment = appointments.selectForUpdate(id);
        if (appointment == null || !Objects.equals(appointment.getStatus(), Constants.APPOINT_ACCEPTED)) return;
        LocalDateTime now = handoffNow();
        int changed = handoffs.markReminded(id, now, now.plusHours(1));
        if (changed == 1) {
            Plan plan = findPlan(id);
            String body = "%s 在%s交接。".formatted(plan.meetingAt().format(DISPLAY_TIME), plan.location());
            sendHandoffNotice(appointment, appointment.getBuyerId(), "闲置交接即将开始", body);
            sendHandoffNotice(appointment, appointment.getSellerId(), "闲置交接即将开始", body);
        }
    }

    private IdleAppointment lockAcceptedAppointment(long id, long userId) {
        IdleAppointment appointment = appointments.selectForUpdate(id);
        requireParticipant(appointment, userId);
        if (!Objects.equals(appointment.getStatus(), Constants.APPOINT_ACCEPTED)) throw invalidHandoff("预约未处于已接受状态");
        return appointment;
    }

    private static void requireParticipant(IdleAppointment appointment, long userId) {
        if (appointment == null) throw new BizException(ResultCode.NOT_FOUND, "预约不存在");
        if (!Objects.equals(appointment.getBuyerId(), userId) && !Objects.equals(appointment.getSellerId(), userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作该预约");
        }
    }

    private Plan findPlan(long id) {
        return handoffs.findPlan(id);
    }

    private void sendHandoffNotice(IdleAppointment appointment, long userId, String title, String body) {
        String type = Objects.equals(appointment.getBuyerId(), userId) ? "handoff_buyer" : "handoff_seller";
        messages.send(userId, Constants.MSG_INTERACT, title, body, type, appointment.getId());
    }

    private static long otherParticipantId(IdleAppointment appointment, long userId) {
        return Objects.equals(appointment.getBuyerId(), userId) ? appointment.getSellerId() : appointment.getBuyerId();
    }

    private static LocalDateTime handoffNow() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }

    private static BizException invalidHandoff(String reason) {
        return new BizException(ResultCode.BAD_REQUEST, reason);
    }

}
