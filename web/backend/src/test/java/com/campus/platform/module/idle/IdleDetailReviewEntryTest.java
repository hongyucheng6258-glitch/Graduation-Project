package com.campus.platform.module.idle;

import com.campus.platform.module.idle.service.IdleService;

import com.campus.platform.module.ai.service.ContentAiAuditService;
import com.campus.platform.module.message.service.MessageService;

import com.campus.platform.module.ai.gateway.SensitiveWordService;
import com.campus.platform.common.Constants;
import com.campus.platform.module.idle.entity.IdleAppointment;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.idle.mapper.IdleAppointmentMapper;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.idle.mapper.IdleReviewMapper;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.module.idle.vo.IdleDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdleDetailReviewEntryTest {

    private static final Long SELLER = 100L;
    private static final Long BUYER = 200L;
    private static final Long ITEM_ID = 1L;
    private static final Long APPOINT_ID = 9L;

    @Mock
    private IdleItemMapper idleItemMapper;
    @Mock
    private IdleAppointmentMapper appointmentMapper;
    @Mock
    private IdleReviewMapper reviewMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private MessageService messageService;
    @Mock
    private SensitiveWordService sensitiveWordService;
    @Mock
    private ContentAiAuditService contentAiAuditService;

    private IdleService idleService;

    @BeforeEach
    void setUp() {
        idleService = new IdleService(
                idleItemMapper,
                appointmentMapper,
                reviewMapper,
                userMapper,
                messageService,
                sensitiveWordService,
                contentAiAuditService);
    }

    @Test
    void detailExposesReviewAppointmentForBuyerAfterFinish() {
        when(idleItemMapper.selectById(ITEM_ID)).thenReturn(item());
        when(appointmentMapper.selectOne(any())).thenReturn(null, appointment());
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        IdleDetailVO vo = idleService.detail(ITEM_ID, BUYER);

        assertFalse(vo.getIsOwner());
        assertEquals(APPOINT_ID, vo.getReviewAppointmentId());
        assertEquals(Boolean.FALSE, vo.getReviewed());
    }

    @Test
    void detailExposesReviewAppointmentForSellerAfterFinish() {
        when(idleItemMapper.selectById(ITEM_ID)).thenReturn(item());
        when(appointmentMapper.selectOne(any())).thenReturn(null, appointment());
        when(reviewMapper.selectCount(any())).thenReturn(1L);
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        IdleDetailVO vo = idleService.detail(ITEM_ID, SELLER);

        assertTrue(vo.getIsOwner());
        assertEquals(APPOINT_ID, vo.getReviewAppointmentId());
        assertEquals(Boolean.TRUE, vo.getReviewed());
    }

    private IdleItem item() {
        IdleItem item = new IdleItem();
        item.setId(ITEM_ID);
        item.setUserId(SELLER);
        item.setTitle("calculator");
        item.setAuditStatus(Constants.AUDIT_PASS);
        item.setStatus(Constants.IDLE_FINISHED);
        item.setViewCount(0);
        return item;
    }

    private IdleAppointment appointment() {
        IdleAppointment appointment = new IdleAppointment();
        appointment.setId(APPOINT_ID);
        appointment.setItemId(ITEM_ID);
        appointment.setBuyerId(BUYER);
        appointment.setSellerId(SELLER);
        appointment.setStatus(Constants.APPOINT_FINISHED);
        return appointment;
    }
}
