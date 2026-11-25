package com.campus.platform.module.idle.service;

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

/**
 * IdleHandoffService 服务接口。
 */
public interface IIdleHandoffService {

    /** 交接计划快照 */
    record Plan(int version, Long proposedBy, LocalDateTime meetingAt, String location,
                boolean confirmed, boolean buyerDone, boolean sellerDone) {}

    void register(long appointmentId);

    Plan view(long userId, long appointmentId);

    Plan propose(long userId, long appointmentId, int version, LocalDateTime meetingAt, String location);

    Plan confirm(long userId, long appointmentId, int version);

    boolean hasCompletionConfirmation(long appointmentId);

    boolean markDone(IdleAppointment appointment, long userId);

    void remind();

    void expireMissedHandoffs();
}
