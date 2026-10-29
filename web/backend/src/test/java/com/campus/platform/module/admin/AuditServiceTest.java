package com.campus.platform.module.admin;

import com.campus.platform.module.admin.service.AuditService;

import com.campus.platform.module.message.service.MessageService;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.post.entity.Post;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.post.mapper.PostMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 先审后发状态机测试（对应 PRD D2 + 架构设计难点3、共享约定 #5）。
 *
 * 状态机：发布后 audit_status=0（待审）→ 通过转 1 → 驳回转 2 + audit_reason；
 * 每次审核结果都必须消息通知作者。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("先审后发-统一审核状态机")
class AuditServiceTest {

    @Mock private IdleItemMapper idleItemMapper;
    @Mock private ActivityMapper activityMapper;
    @Mock private LostFoundMapper lostFoundMapper;
    @Mock private PostMapper postMapper;
    @Mock private MessageService messageService;

    @InjectMocks
    private AuditService auditService;

    // ==================== 状态机常量口径 ====================

    @Test
    @DisplayName("审核状态常量必须符合共享约定 #5（0待审/1通过/2驳回）")
    void auditStatusConstants_shouldMatchSharedContract() {
        assertThat(Constants.AUDIT_PENDING).isZero();
        assertThat(Constants.AUDIT_PASS).isEqualTo(1);
        assertThat(Constants.AUDIT_REJECT).isEqualTo(2);
    }

    @Test
    @DisplayName("四类 UGC 的 bizType 常量必须与接口文档一致")
    void bizTypeConstants_shouldMatchApiSpec() {
        assertThat(Constants.BIZ_IDLE).isEqualTo("idle");
        assertThat(Constants.BIZ_ACTIVITY).isEqualTo("activity");
        assertThat(Constants.BIZ_LOSTFOUND).isEqualTo("lostfound");
        assertThat(Constants.BIZ_POST).isEqualTo("post");
    }

    // ==================== 闲置 ====================

    @Nested
    @DisplayName("闲置(idle)审核")
    class IdleAudit {

        @Test
        @DisplayName("通过应置 audit_status=1、清空驳回理由，并通知作者")
        void pass_shouldSetStatusOneAndNotify() {
            IdleItem item = buildIdle(10L, 5L, "九成新自行车");
            when(idleItemMapper.selectById(10L)).thenReturn(item);

            auditService.pass(Constants.BIZ_IDLE, 10L);

            ArgumentCaptor<IdleItem> captor = ArgumentCaptor.forClass(IdleItem.class);
            verify(idleItemMapper).updateById(captor.capture());
            assertThat(captor.getValue().getAuditStatus()).isEqualTo(Constants.AUDIT_PASS);
            assertThat(captor.getValue().getAuditReason()).isNull();

            verify(messageService).send(eq(5L), eq(Constants.MSG_AUDIT), eq("审核通过"),
                    anyString(), eq(Constants.BIZ_IDLE), eq(10L));
        }

        @Test
        @DisplayName("驳回应置 audit_status=2、写入理由，且理由随消息送达作者")
        void reject_shouldSetStatusTwoWithReasonAndNotify() {
            IdleItem item = buildIdle(10L, 5L, "违规商品");
            when(idleItemMapper.selectById(10L)).thenReturn(item);

            auditService.reject(Constants.BIZ_IDLE, 10L, "涉嫌售卖违禁品");

            ArgumentCaptor<IdleItem> captor = ArgumentCaptor.forClass(IdleItem.class);
            verify(idleItemMapper).updateById(captor.capture());
            assertThat(captor.getValue().getAuditStatus()).isEqualTo(Constants.AUDIT_REJECT);
            assertThat(captor.getValue().getAuditReason()).isEqualTo("涉嫌售卖违禁品");

            // D2 验收：驳回理由必须随消息送达
            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
            verify(messageService).send(eq(5L), eq(Constants.MSG_AUDIT), eq("审核未通过"),
                    contentCaptor.capture(), eq(Constants.BIZ_IDLE), eq(10L));
            assertThat(contentCaptor.getValue()).contains("涉嫌售卖违禁品");
            assertThat(contentCaptor.getValue()).contains("违规商品");
        }

        @Test
        @DisplayName("内容不存在应 404，且不发通知")
        void pass_shouldThrowNotFound() {
            when(idleItemMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> auditService.pass(Constants.BIZ_IDLE, 999L))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.NOT_FOUND.getCode());

            verify(messageService, never()).send(anyLong(), anyString(), anyString(),
                    anyString(), anyString(), any());
        }
    }

    // ==================== 活动 ====================

    @Nested
    @DisplayName("活动(activity)审核")
    class ActivityAudit {

        @Test
        @DisplayName("通过应置 audit_status=1 并通知发布者")
        void pass_shouldSetStatusOne() {
            Activity activity = new Activity();
            activity.setId(20L);
            activity.setUserId(6L);
            activity.setTitle("周末篮球局");
            activity.setAuditStatus(Constants.AUDIT_PENDING);
            when(activityMapper.selectById(20L)).thenReturn(activity);

            auditService.pass(Constants.BIZ_ACTIVITY, 20L);

            assertThat(activity.getAuditStatus()).isEqualTo(Constants.AUDIT_PASS);
            verify(activityMapper).updateById(activity);
            verify(messageService).send(eq(6L), eq(Constants.MSG_AUDIT), eq("审核通过"),
                    anyString(), anyString(), any());
        }

        @Test
        @DisplayName("驳回应置 2 并带理由")
        void reject_shouldSetStatusTwo() {
            Activity activity = new Activity();
            activity.setId(20L);
            activity.setUserId(6L);
            activity.setTitle("违规活动");
            when(activityMapper.selectById(20L)).thenReturn(activity);

            auditService.reject(Constants.BIZ_ACTIVITY, 20L, "活动信息不完整");

            assertThat(activity.getAuditStatus()).isEqualTo(Constants.AUDIT_REJECT);
            assertThat(activity.getAuditReason()).isEqualTo("活动信息不完整");
        }
    }

    // ==================== 失物招领 ====================

    @Nested
    @DisplayName("失物招领(lostfound)审核")
    class LostFoundAudit {

        @Test
        @DisplayName("通过应置 audit_status=1 并通知作者")
        void pass_shouldSetStatusOne() {
            LostFound lf = new LostFound();
            lf.setId(30L);
            lf.setUserId(7L);
            lf.setTitle("捡到学生卡");
            when(lostFoundMapper.selectById(30L)).thenReturn(lf);

            auditService.pass(Constants.BIZ_LOSTFOUND, 30L);

            assertThat(lf.getAuditStatus()).isEqualTo(Constants.AUDIT_PASS);
            verify(messageService).send(eq(7L), anyString(), anyString(), anyString(), anyString(), any());
        }
    }

    // ==================== 动态 ====================

    @Nested
    @DisplayName("动态(post)审核")
    class PostAudit {

        @Test
        @DisplayName("通过应置 audit_status=1，通知内容用正文前20字预览")
        void pass_shouldTruncateLongContentPreview() {
            Post post = new Post();
            post.setId(40L);
            post.setUserId(8L);
            post.setContent("这是一条超过二十个字的动态正文内容用于验证预览截断逻辑是否正确工作");
            when(postMapper.selectById(40L)).thenReturn(post);

            auditService.pass(Constants.BIZ_POST, 40L);

            assertThat(post.getAuditStatus()).isEqualTo(Constants.AUDIT_PASS);
            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
            verify(messageService).send(eq(8L), anyString(), anyString(),
                    contentCaptor.capture(), anyString(), any());
            assertThat(contentCaptor.getValue()).contains("...");
        }

        @Test
        @DisplayName("短正文不应被截断，也不得抛越界异常")
        void pass_shouldHandleShortContent() {
            Post post = new Post();
            post.setId(41L);
            post.setUserId(8L);
            post.setContent("短动态");
            when(postMapper.selectById(41L)).thenReturn(post);

            auditService.pass(Constants.BIZ_POST, 41L);

            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
            verify(messageService).send(eq(8L), anyString(), anyString(),
                    contentCaptor.capture(), anyString(), any());
            assertThat(contentCaptor.getValue()).contains("短动态").doesNotContain("...");
        }
    }

    // ==================== 异常路由 ====================

    @Test
    @DisplayName("不支持的审核类型应返回 400，不得静默成功")
    void pass_shouldRejectUnsupportedType() {
        assertThatThrownBy(() -> auditService.pass("comment", 1L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode())
                .hasMessageContaining("不支持的审核类型");
    }

    @Test
    @DisplayName("待审队列不支持的类型同样应 400")
    void pendingList_shouldRejectUnsupportedType() {
        assertThatThrownBy(() -> auditService.pendingList("unknown", 1, 10))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode());
    }

    private static IdleItem buildIdle(Long id, Long userId, String title) {
        IdleItem item = new IdleItem();
        item.setId(id);
        item.setUserId(userId);
        item.setTitle(title);
        item.setAuditStatus(Constants.AUDIT_PENDING);
        return item;
    }
}
