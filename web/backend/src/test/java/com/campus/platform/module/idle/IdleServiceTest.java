package com.campus.platform.module.idle;

import com.campus.platform.module.idle.mapper.IdleAppointmentMapper;
import com.campus.platform.module.idle.mapper.IdleReviewMapper;
import com.campus.platform.module.idle.entity.IdleAppointment;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.idle.vo.IdleDetailVO;
import com.campus.platform.module.idle.service.IdleService;
import com.campus.platform.module.idle.dto.AppointDTO;
import com.campus.platform.module.idle.entity.IdleReview;
import com.campus.platform.module.idle.entity.IdleItem;

import com.campus.platform.module.message.service.MessageService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.post.dto.ReviewDTO;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C1 闲置互换闭环测试：发布 → 审核通过 → 预约 → 卖家确认 → 完成 → 互评。
 * 重点验收 PRD C1「完整走通 发布→审核通过→预约→互换→评价 闭环」。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("C1 闲置互换 - 预约/确认/完成/互评闭环")
class IdleServiceTest {

    private static final Long SELLER = 100L;
    private static final Long BUYER = 200L;
    private static final Long OTHER = 300L;
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

    @InjectMocks
    private IdleService idleService;

    private IdleItem item(int auditStatus, int status) {
        IdleItem it = new IdleItem();
        it.setId(ITEM_ID);
        it.setUserId(SELLER);
        it.setTitle("九成新计算器");
        it.setAuditStatus(auditStatus);
        it.setStatus(status);
        it.setViewCount(0);
        return it;
    }

    private IdleAppointment appointment(int status) {
        IdleAppointment a = new IdleAppointment();
        a.setId(APPOINT_ID);
        a.setItemId(ITEM_ID);
        a.setBuyerId(BUYER);
        a.setSellerId(SELLER);
        a.setStatus(status);
        return a;
    }

    private AppointDTO appointDto() {
        AppointDTO d = new AppointDTO();
        d.setMessage("想用一本高数书换");
        return d;
    }

    // ==================== 详情可见性 ====================

    @Nested
    @DisplayName("详情可见性（先审后发）")
    class Detail {

        @Test
        @DisplayName("待审核物品：非本人访问返回 1003，不泄露内容")
        void detail_pendingNotOwner_shouldReject() {
            when(idleItemMapper.selectById(ITEM_ID))
                    .thenReturn(item(Constants.AUDIT_PENDING, Constants.IDLE_ON_SHELF));

            assertThatThrownBy(() -> idleService.detail(ITEM_ID, OTHER))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.AUDIT_PENDING.getCode());
            // 未通过审核不应产生浏览数写入
            verify(idleItemMapper, never()).updateById(any(IdleItem.class));
        }

        @Test
        @DisplayName("待审核物品：本人可见，isOwner=true")
        void detail_pendingOwner_shouldPass() {
            when(idleItemMapper.selectById(ITEM_ID))
                    .thenReturn(item(Constants.AUDIT_PENDING, Constants.IDLE_ON_SHELF));

            IdleDetailVO vo = idleService.detail(ITEM_ID, SELLER);

            assertThat(vo.getIsOwner()).isTrue();
        }

        @Test
        @DisplayName("游客（uid=null）看已过审物品不报 NPE，isOwner=false")
        void detail_guest_shouldNotThrow() {
            when(idleItemMapper.selectById(ITEM_ID))
                    .thenReturn(item(Constants.AUDIT_PASS, Constants.IDLE_ON_SHELF));

            IdleDetailVO vo = idleService.detail(ITEM_ID, null);

            assertThat(vo.getIsOwner()).isFalse();
            assertThat(vo.getMyAppointmentId()).isNull();
        }

        @Test
        @DisplayName("物品不存在返回 404")
        void detail_notFound() {
            when(idleItemMapper.selectById(ITEM_ID)).thenReturn(null);

            assertThatThrownBy(() -> idleService.detail(ITEM_ID, BUYER))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.NOT_FOUND.getCode());
        }
    }

    // ==================== 预约 ====================

    @Nested
    @DisplayName("预约")
    class Appoint {

        @Test
        @DisplayName("正常预约：落库待确认 + 物品转已预约 + 通知卖家")
        void appoint_happyPath() {
            when(idleItemMapper.selectById(ITEM_ID))
                    .thenReturn(item(Constants.AUDIT_PASS, Constants.IDLE_ON_SHELF));
            when(idleItemMapper.update(org.mockito.ArgumentMatchers.<IdleItem>isNull(),
                    org.mockito.ArgumentMatchers.<Wrapper<IdleItem>>any())).thenReturn(1);
            User buyer = new User();
            buyer.setId(BUYER);
            buyer.setNickname("小明");
            when(userMapper.selectById(BUYER)).thenReturn(buyer);

            IdleAppointment saved = idleService.appoint(BUYER, ITEM_ID, appointDto());

            assertThat(saved.getStatus()).isEqualTo(Constants.APPOINT_PENDING);
            assertThat(saved.getBuyerId()).isEqualTo(BUYER);
            assertThat(saved.getSellerId()).isEqualTo(SELLER);

            ArgumentCaptor<Wrapper<IdleItem>> reservation = ArgumentCaptor.forClass(Wrapper.class);
            verify(idleItemMapper).update(org.mockito.ArgumentMatchers.<IdleItem>isNull(), reservation.capture());
            UpdateWrapper<IdleItem> condition = (UpdateWrapper<IdleItem>) reservation.getValue();
            assertThat(condition.getSqlSet()).contains("status");
            assertThat(condition.getSqlSegment()).contains("id", "audit_status", "status");
            verify(idleItemMapper, never()).updateById(any(IdleItem.class));

            verify(messageService).send(eq(SELLER), eq(Constants.MSG_INTERACT),
                    anyString(), anyString(), eq(Constants.BIZ_IDLE), any());
            verify(messageService).send(eq(BUYER), eq(Constants.MSG_INTERACT),
                    eq("预约已提交"), anyString(), eq(Constants.BIZ_IDLE), eq(ITEM_ID));
        }

        @Test
        @DisplayName("未过审物品不可预约 → 404")
        void appoint_notAudited() {
            when(idleItemMapper.selectById(ITEM_ID))
                    .thenReturn(item(Constants.AUDIT_PENDING, Constants.IDLE_ON_SHELF));

            assertThatThrownBy(() -> idleService.appoint(BUYER, ITEM_ID, appointDto()))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.NOT_FOUND.getCode());
            verify(appointmentMapper, never()).insert(any(IdleAppointment.class));
        }

        @Test
        @DisplayName("不能预约自己发布的物品 → 400")
        void appoint_self() {
            when(idleItemMapper.selectById(ITEM_ID))
                    .thenReturn(item(Constants.AUDIT_PASS, Constants.IDLE_ON_SHELF));

            assertThatThrownBy(() -> idleService.appoint(SELLER, ITEM_ID, appointDto()))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode());
        }

        @Test
        @DisplayName("已被预约的物品再次预约 → 1005 重复操作")
        void appoint_alreadyReserved() {
            when(idleItemMapper.selectById(ITEM_ID))
                    .thenReturn(item(Constants.AUDIT_PASS, Constants.IDLE_RESERVED));

            assertThatThrownBy(() -> idleService.appoint(BUYER, ITEM_ID, appointDto()))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.DUPLICATE_OPERATION.getCode());
        }

        @Test
        @DisplayName("并发预约同一商品：只允许一个请求创建预约")
        void appoint_concurrentBuyers_onlyOneCanReserve() throws Exception {
            CountDownLatch readers = new CountDownLatch(2);
            CountDownLatch releaseReaders = new CountDownLatch(1);
            AtomicBoolean claimed = new AtomicBoolean();
            when(idleItemMapper.selectById(ITEM_ID)).thenAnswer(invocation -> {
                readers.countDown();
                if (!releaseReaders.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("两名买家未同时完成商品读取");
                }
                return item(Constants.AUDIT_PASS, Constants.IDLE_ON_SHELF);
            });
            when(idleItemMapper.update(org.mockito.ArgumentMatchers.<IdleItem>isNull(),
                    org.mockito.ArgumentMatchers.<Wrapper<IdleItem>>any()))
                    .thenAnswer(invocation -> claimed.compareAndSet(false, true) ? 1 : 0);
            when(userMapper.selectById(anyLong())).thenAnswer(invocation -> {
                User buyer = new User();
                buyer.setId(invocation.getArgument(0));
                buyer.setNickname("QA 买家");
                return buyer;
            });

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> first = pool.submit(() -> appointResult(BUYER));
                Future<Integer> second = pool.submit(() -> appointResult(OTHER));
                assertThat(readers.await(5, TimeUnit.SECONDS)).isTrue();
                releaseReaders.countDown();

                List<Integer> results = List.of(
                        first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
                assertThat(results).containsExactlyInAnyOrder(
                        200, ResultCode.DUPLICATE_OPERATION.getCode());
                verify(appointmentMapper, times(1)).insert(any(IdleAppointment.class));
                verify(idleItemMapper, times(2)).update(
                        org.mockito.ArgumentMatchers.<IdleItem>isNull(),
                        org.mockito.ArgumentMatchers.<Wrapper<IdleItem>>any());
            } finally {
                releaseReaders.countDown();
                pool.shutdownNow();
            }
        }

        private int appointResult(Long buyerId) {
            try {
                idleService.appoint(buyerId, ITEM_ID, appointDto());
                return 200;
            } catch (BizException e) {
                return e.getCode();
            }
        }
    }

    // ==================== 卖家处理 ====================

    @Nested
    @DisplayName("卖家处理预约")
    class Handle {

        @Test
        @DisplayName("接受：预约转已接受 + 通知买家 + 物品保持已预约")
        void handle_accept() {
            when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(appointment(Constants.APPOINT_PENDING));
            when(idleItemMapper.selectById(ITEM_ID)).thenReturn(item(Constants.AUDIT_PASS, Constants.IDLE_RESERVED));

            idleService.handleAppoint(SELLER, APPOINT_ID, true);

            ArgumentCaptor<IdleAppointment> cap = ArgumentCaptor.forClass(IdleAppointment.class);
            verify(appointmentMapper).updateById(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo(Constants.APPOINT_ACCEPTED);
            verify(idleItemMapper, never()).updateById(any(IdleItem.class));
            verify(messageService).send(eq(BUYER), eq(Constants.MSG_INTERACT),
                    anyString(), anyString(), eq(Constants.BIZ_IDLE), any());
        }

        @Test
        @DisplayName("拒绝：预约转已拒绝 + 物品必须恢复在架（否则永久锁死）")
        void handle_reject_shouldRestoreOnShelf() {
            when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(appointment(Constants.APPOINT_PENDING));
            when(idleItemMapper.selectById(ITEM_ID)).thenReturn(item(Constants.AUDIT_PASS, Constants.IDLE_RESERVED));

            idleService.handleAppoint(SELLER, APPOINT_ID, false);

            ArgumentCaptor<IdleAppointment> cap = ArgumentCaptor.forClass(IdleAppointment.class);
            verify(appointmentMapper).updateById(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo(Constants.APPOINT_REJECTED);

            ArgumentCaptor<IdleItem> itemCap = ArgumentCaptor.forClass(IdleItem.class);
            verify(idleItemMapper).updateById(itemCap.capture());
            assertThat(itemCap.getValue().getStatus())
                    .as("拒绝预约后物品必须回到在架，否则该闲置再也无人能预约")
                    .isEqualTo(Constants.IDLE_ON_SHELF);
        }

        @Test
        @DisplayName("非卖家处理 → 403")
        void handle_notSeller() {
            when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(appointment(Constants.APPOINT_PENDING));

            assertThatThrownBy(() -> idleService.handleAppoint(OTHER, APPOINT_ID, true))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
        }

        @Test
        @DisplayName("重复处理已接受的预约 → 1005")
        void handle_duplicate() {
            when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(appointment(Constants.APPOINT_ACCEPTED));

            assertThatThrownBy(() -> idleService.handleAppoint(SELLER, APPOINT_ID, true))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.DUPLICATE_OPERATION.getCode());
        }
    }

    // ==================== 完成 ====================

    @Nested
    @DisplayName("确认完成")
    class Finish {

        @Test
        @DisplayName("买家确认完成：预约+物品均转完成，并通知卖家（对方）")
        void finish_byBuyer() {
            when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(appointment(Constants.APPOINT_ACCEPTED));
            when(idleItemMapper.selectById(ITEM_ID)).thenReturn(item(Constants.AUDIT_PASS, Constants.IDLE_RESERVED));

            idleService.finishAppoint(BUYER, APPOINT_ID);

            ArgumentCaptor<IdleAppointment> cap = ArgumentCaptor.forClass(IdleAppointment.class);
            verify(appointmentMapper).updateById(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo(Constants.APPOINT_FINISHED);

            ArgumentCaptor<IdleItem> itemCap = ArgumentCaptor.forClass(IdleItem.class);
            verify(idleItemMapper).updateById(itemCap.capture());
            assertThat(itemCap.getValue().getStatus()).isEqualTo(Constants.IDLE_FINISHED);

            verify(messageService).send(eq(SELLER), eq(Constants.MSG_INTERACT),
                    anyString(), anyString(), eq(Constants.BIZ_IDLE), any());
        }

        @Test
        @DisplayName("卖家确认完成：通知对象应为买家")
        void finish_bySeller_notifiesBuyer() {
            when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(appointment(Constants.APPOINT_ACCEPTED));
            when(idleItemMapper.selectById(ITEM_ID)).thenReturn(item(Constants.AUDIT_PASS, Constants.IDLE_RESERVED));

            idleService.finishAppoint(SELLER, APPOINT_ID);

            verify(messageService).send(eq(BUYER), eq(Constants.MSG_INTERACT),
                    anyString(), anyString(), eq(Constants.BIZ_IDLE), any());
        }

        @Test
        @DisplayName("无关用户确认 → 403")
        void finish_stranger() {
            when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(appointment(Constants.APPOINT_ACCEPTED));

            assertThatThrownBy(() -> idleService.finishAppoint(OTHER, APPOINT_ID))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
        }

        @Test
        @DisplayName("尚未接受的预约不能直接完成 → 400")
        void finish_notAccepted() {
            when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(appointment(Constants.APPOINT_PENDING));

            assertThatThrownBy(() -> idleService.finishAppoint(BUYER, APPOINT_ID))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode());
        }
    }

    // ==================== 互评 ====================

    @Nested
    @DisplayName("互评")
    class Review {

        private ReviewDTO dto(int score) {
            ReviewDTO d = new ReviewDTO();
            d.setScore(score);
            d.setContent("很靠谱，交易顺利");
            return d;
        }

        @Test
        @DisplayName("买家评价：评价对象自动指向卖家")
        void review_buyerToSeller() {
            when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(appointment(Constants.APPOINT_FINISHED));
            when(reviewMapper.selectCount(any())).thenReturn(0L);

            idleService.review(BUYER, APPOINT_ID, dto(5));

            ArgumentCaptor<IdleReview> cap = ArgumentCaptor.forClass(IdleReview.class);
            verify(reviewMapper).insert(cap.capture());
            assertThat(cap.getValue().getFromUserId()).isEqualTo(BUYER);
            assertThat(cap.getValue().getToUserId()).isEqualTo(SELLER);
            assertThat(cap.getValue().getScore()).isEqualTo(5);
        }

        @Test
        @DisplayName("卖家评价：评价对象自动指向买家（双向互评）")
        void review_sellerToBuyer() {
            when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(appointment(Constants.APPOINT_FINISHED));
            when(reviewMapper.selectCount(any())).thenReturn(0L);

            idleService.review(SELLER, APPOINT_ID, dto(4));

            ArgumentCaptor<IdleReview> cap = ArgumentCaptor.forClass(IdleReview.class);
            verify(reviewMapper).insert(cap.capture());
            assertThat(cap.getValue().getFromUserId()).isEqualTo(SELLER);
            assertThat(cap.getValue().getToUserId()).isEqualTo(BUYER);
        }

        @Test
        @DisplayName("交易未完成不能评价 → 400")
        void review_beforeFinish() {
            when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(appointment(Constants.APPOINT_ACCEPTED));

            assertThatThrownBy(() -> idleService.review(BUYER, APPOINT_ID, dto(5)))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode());
            verify(reviewMapper, never()).insert(any(IdleReview.class));
        }

        @Test
        @DisplayName("无关用户不能评价 → 403")
        void review_stranger() {
            when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(appointment(Constants.APPOINT_FINISHED));

            assertThatThrownBy(() -> idleService.review(OTHER, APPOINT_ID, dto(5)))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
        }

        @Test
        @DisplayName("同一人重复评价同一笔 → 1005（应用层去重，兜底联合唯一索引）")
        void review_duplicate() {
            when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(appointment(Constants.APPOINT_FINISHED));
            when(reviewMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> idleService.review(BUYER, APPOINT_ID, dto(5)))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.DUPLICATE_OPERATION.getCode());
            verify(reviewMapper, never()).insert(any(IdleReview.class));
        }

        @Test
        @DisplayName("预约不存在 → 404")
        void review_appointNotFound() {
            when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(null);

            assertThatThrownBy(() -> idleService.review(BUYER, APPOINT_ID, dto(5)))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.NOT_FOUND.getCode());
        }
    }

    // ==================== 全链路 ====================

    @Test
    @DisplayName("【全链路】预约 → 接受 → 完成 → 双向互评 状态严格推进")
    void fullLoop_shouldAdvanceStatusStrictly() {
        // 1) 预约
        IdleItem it = item(Constants.AUDIT_PASS, Constants.IDLE_ON_SHELF);
        when(idleItemMapper.selectById(ITEM_ID)).thenReturn(it);
        when(idleItemMapper.update(org.mockito.ArgumentMatchers.<IdleItem>isNull(),
                org.mockito.ArgumentMatchers.<Wrapper<IdleItem>>any())).thenReturn(1);
        when(userMapper.selectById(BUYER)).thenReturn(new User());
        IdleAppointment created = idleService.appoint(BUYER, ITEM_ID, appointDto());
        assertThat(created.getStatus()).isEqualTo(Constants.APPOINT_PENDING);
        verify(idleItemMapper).update(org.mockito.ArgumentMatchers.<IdleItem>isNull(),
                org.mockito.ArgumentMatchers.<Wrapper<IdleItem>>any());

        // 2) 卖家接受
        created.setId(APPOINT_ID);
        when(appointmentMapper.selectById(APPOINT_ID)).thenReturn(created);
        idleService.handleAppoint(SELLER, APPOINT_ID, true);
        assertThat(created.getStatus()).isEqualTo(Constants.APPOINT_ACCEPTED);

        // 3) 完成
        idleService.finishAppoint(BUYER, APPOINT_ID);
        assertThat(created.getStatus()).isEqualTo(Constants.APPOINT_FINISHED);
        assertThat(it.getStatus()).isEqualTo(Constants.IDLE_FINISHED);

        // 4) 双向互评各一次
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        ReviewDTO r = new ReviewDTO();
        r.setScore(5);
        r.setContent("好");
        idleService.review(BUYER, APPOINT_ID, r);
        idleService.review(SELLER, APPOINT_ID, r);
        verify(reviewMapper, org.mockito.Mockito.times(2)).insert(any(IdleReview.class));
    }
}
