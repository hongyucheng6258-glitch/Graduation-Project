package com.campus.platform.module.activity;

import com.campus.platform.module.activity.mapper.ActivityMemberMapper;
import com.campus.platform.module.activity.mapper.ActivitySigninMapper;
import com.campus.platform.module.activity.service.ActivityService;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.activity.entity.ActivityMember;
import com.campus.platform.module.activity.dto.SignupDTO;
import com.campus.platform.module.activity.entity.Activity;

import com.campus.platform.module.ai.service.ContentAiAuditService;
import com.campus.platform.module.message.service.MessageService;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.utils.SignTokenUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("活动状态动态计算与报名强校验")
class ActivityServiceTest {

    @BeforeAll
    static void initializeMapperMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "activity-test"),
                ActivityMember.class);
    }

    private static final Long USER_ID = 1L;
    private static final Long PUBLISHER_ID = 2L;
    private static final Long ACTIVITY_ID = 10L;

    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private ActivityMemberMapper memberMapper;
    @Mock
    private ActivitySigninMapper signinMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private MessageService messageService;
    @Mock
    private SignTokenUtils signTokenUtils;
    @Mock
    private SensitiveWordService sensitiveWordService;
    @Mock
    private ContentAiAuditService contentAiAuditService;

    @InjectMocks
    private ActivityService service;

    private Activity activity(int status, LocalDateTime start, LocalDateTime end,
                              LocalDateTime deadline, int maxMembers) {
        Activity a = new Activity();
        a.setId(ACTIVITY_ID);
        a.setUserId(PUBLISHER_ID);
        a.setTitle("测试活动");
        a.setStatus(status);
        a.setAuditStatus(Constants.AUDIT_PASS);
        a.setStartTime(start);
        a.setEndTime(end);
        a.setSignupDeadline(deadline);
        a.setMaxMembers(maxMembers);
        return a;
    }

    // ---------- resolveDisplayStatus：时间/人数动态状态 ----------

    // 用系统当前时间动态构造（避免固定日期与实际运行时间错位）
    private static final LocalDateTime NOW = LocalDateTime.now();
    private static final LocalDateTime PAST = NOW.minusDays(1);
    private static final LocalDateTime FUTURE = NOW.plusDays(1);

    private ActivityMember pendingMember() {
        ActivityMember member = new ActivityMember();
        member.setId(20L);
        member.setActivityId(ACTIVITY_ID);
        member.setUserId(USER_ID);
        member.setStatus(Constants.MEMBER_PENDING);
        return member;
    }

    @Test
    void approval_locksActivityBeforeCheckingCapacityAndApprovesLastSeat() {
        Activity a = activity(Constants.ACTIVITY_SIGNING, FUTURE, FUTURE.plusHours(2), FUTURE, 1);
        when(memberMapper.selectById(20L)).thenReturn(pendingMember());
        when(activityMapper.selectForUpdate(ACTIVITY_ID)).thenReturn(a);
        when(memberMapper.selectCount(any())).thenReturn(0L, 1L);
        when(memberMapper.update(org.mockito.ArgumentMatchers.<ActivityMember>isNull(), any())).thenReturn(1);
        service.handleMember(PUBLISHER_ID, 20L, true);
        var order = inOrder(memberMapper, activityMapper);
        order.verify(memberMapper).selectById(20L);
        order.verify(activityMapper).selectForUpdate(ACTIVITY_ID);
        order.verify(memberMapper).selectById(20L);
        order.verify(memberMapper).selectCount(any());
        assertThat(a.getStatus()).isEqualTo(Constants.ACTIVITY_FULL);
        verify(messageService).send(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void approval_rejectsFullActivityWithoutChangingMember() {
        when(memberMapper.selectById(20L)).thenReturn(pendingMember());
        when(activityMapper.selectForUpdate(ACTIVITY_ID)).thenReturn(
                activity(Constants.ACTIVITY_FULL, FUTURE, FUTURE.plusHours(2), FUTURE, 1));
        when(memberMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.handleMember(PUBLISHER_ID, 20L, true))
                .isInstanceOf(BizException.class).hasMessageContaining("人数已满");
        verify(memberMapper, never()).update(org.mockito.ArgumentMatchers.<ActivityMember>any(), any());
        verifyNoInteractions(messageService);
    }

    @Test
    void approval_rechecksStatusAfterWaitingForLock() {
        ActivityMember handled = pendingMember();
        handled.setStatus(Constants.MEMBER_APPROVED);
        when(memberMapper.selectById(20L)).thenReturn(pendingMember(), handled);
        when(activityMapper.selectForUpdate(ACTIVITY_ID)).thenReturn(
                activity(Constants.ACTIVITY_SIGNING, FUTURE, FUTURE.plusHours(2), FUTURE, 1));
        assertThatThrownBy(() -> service.handleMember(PUBLISHER_ID, 20L, true))
                .isInstanceOf(BizException.class).hasMessageContaining("已审批");
        verifyNoInteractions(messageService);
    }

    @Test
    void approval_doesNotNotifyIfMemberWasCancelledBeforeUpdate() {
        when(memberMapper.selectById(20L)).thenReturn(pendingMember());
        when(activityMapper.selectForUpdate(ACTIVITY_ID)).thenReturn(
                activity(Constants.ACTIVITY_SIGNING, FUTURE, FUTURE.plusHours(2), FUTURE, 0));
        assertThatThrownBy(() -> service.handleMember(PUBLISHER_ID, 20L, true))
                .isInstanceOf(BizException.class).hasMessageContaining("已取消");
        verifyNoInteractions(messageService);
    }

    @Test
    @DisplayName("未开始、未截止、未满员 → 报名中且可报名")
    void resolve_signing() {
        Activity a = activity(Constants.ACTIVITY_SIGNING, FUTURE, FUTURE.plusHours(2), FUTURE, 10);
        ActivityService.DisplayInfo info = ActivityService.resolveDisplayStatus(a, NOW, 0);
        assertThat(info.status).isEqualTo(Constants.ACT_DISPLAY_SIGNING);
        assertThat(info.text).isEqualTo("报名中");
        assertThat(info.canSignup).isTrue();
        assertThat(info.reason).isNull();
    }

    @Test
    @DisplayName("结束时间已过（即使数据库仍是报名中）→ 已结束，不可报名")
    void resolve_endedByTime() {
        Activity a = activity(Constants.ACTIVITY_SIGNING, PAST, PAST.plusHours(1), FUTURE, 10);
        ActivityService.DisplayInfo info = ActivityService.resolveDisplayStatus(a, NOW, 0);
        assertThat(info.status).isEqualTo(Constants.ACT_DISPLAY_ENDED);
        assertThat(info.canSignup).isFalse();
        assertThat(info.reason).isEqualTo("活动已结束");
    }

    @Test
    @DisplayName("数据库已结束状态 → 已结束")
    void resolve_endedByDb() {
        Activity a = activity(Constants.ACTIVITY_ENDED, FUTURE, FUTURE.plusHours(2), FUTURE, 10);
        ActivityService.DisplayInfo info = ActivityService.resolveDisplayStatus(a, NOW, 0);
        assertThat(info.status).isEqualTo(Constants.ACT_DISPLAY_ENDED);
    }

    @Test
    @DisplayName("审批通过数达到上限 → 已满员")
    void resolve_full() {
        Activity a = activity(Constants.ACTIVITY_SIGNING, FUTURE, FUTURE.plusHours(2), FUTURE, 10);
        ActivityService.DisplayInfo info = ActivityService.resolveDisplayStatus(a, NOW, 10);
        assertThat(info.status).isEqualTo(Constants.ACT_DISPLAY_FULL);
        assertThat(info.reason).isEqualTo("活动人数已满");
    }

    @Test
    @DisplayName("报名截止时间已过 → 报名已截止")
    void resolve_deadlinePassed() {
        Activity a = activity(Constants.ACTIVITY_SIGNING, FUTURE, FUTURE.plusHours(2), PAST, 10);
        ActivityService.DisplayInfo info = ActivityService.resolveDisplayStatus(a, NOW, 0);
        assertThat(info.status).isEqualTo(Constants.ACT_DISPLAY_DEADLINE_PASSED);
        assertThat(info.reason).isEqualTo("报名已截止");
    }

    @Test
    @DisplayName("未设报名截止且活动已开始 → 活动进行中，不可报名")
    void resolve_ongoingWhenNoDeadline() {
        Activity a = activity(Constants.ACTIVITY_SIGNING, PAST, FUTURE, null, 10);
        ActivityService.DisplayInfo info = ActivityService.resolveDisplayStatus(a, NOW, 0);
        assertThat(info.status).isEqualTo(Constants.ACT_DISPLAY_ONGOING);
        assertThat(info.reason).isEqualTo("活动已开始，无法报名");
    }

    @Test
    @DisplayName("已下架 → 已下架，不可报名")
    void resolve_offShelf() {
        Activity a = activity(Constants.ACTIVITY_OFF, FUTURE, FUTURE.plusHours(2), FUTURE, 10);
        ActivityService.DisplayInfo info = ActivityService.resolveDisplayStatus(a, NOW, 0);
        assertThat(info.status).isEqualTo(Constants.ACT_DISPLAY_OFF);
        assertThat(info.reason).isEqualTo("活动已下架");
    }

    // ---------- signup：报名接口最终校验 ----------

    private void mockPassedActivity(Activity a) {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(a);
    }

    private User user() {
        User u = new User();
        u.setId(PUBLISHER_ID);
        u.setNickname("发布者");
        return u;
    }

    @Test
    @DisplayName("活动已结束 → 拒绝报名")
    void signup_rejectEnded() {
        mockPassedActivity(activity(Constants.ACTIVITY_SIGNING, PAST, PAST.plusHours(1), FUTURE, 10));
        assertThatThrownBy(() -> service.signup(USER_ID, ACTIVITY_ID, new SignupDTO()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("活动已结束");
    }

    @Test
    @DisplayName("报名截止 → 拒绝报名")
    void signup_rejectDeadline() {
        mockPassedActivity(activity(Constants.ACTIVITY_SIGNING, FUTURE, FUTURE.plusHours(2), PAST, 10));
        assertThatThrownBy(() -> service.signup(USER_ID, ACTIVITY_ID, new SignupDTO()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("报名已截止");
    }

    @Test
    @DisplayName("未设报名截止且活动已开始 → 拒绝报名")
    void signup_rejectOngoingNoDeadline() {
        mockPassedActivity(activity(Constants.ACTIVITY_SIGNING, PAST, FUTURE, null, 10));
        assertThatThrownBy(() -> service.signup(USER_ID, ACTIVITY_ID, new SignupDTO()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("活动已开始，无法报名");
    }

    @Test
    @DisplayName("已满员 → 拒绝报名")
    void signup_rejectFull() {
        mockPassedActivity(activity(Constants.ACTIVITY_SIGNING, FUTURE, FUTURE.plusHours(2), FUTURE, 10));
        when(memberMapper.selectCount(any())).thenReturn(10L);
        assertThatThrownBy(() -> service.signup(USER_ID, ACTIVITY_ID, new SignupDTO()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("活动人数已满");
    }

    @Test
    @DisplayName("已下架 → 拒绝报名")
    void signup_rejectOff() {
        mockPassedActivity(activity(Constants.ACTIVITY_OFF, FUTURE, FUTURE.plusHours(2), FUTURE, 10));
        assertThatThrownBy(() -> service.signup(USER_ID, ACTIVITY_ID, new SignupDTO()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("活动已下架");
    }

    @Test
    @DisplayName("未审核通过 → 拒绝报名")
    void signup_rejectNotPassed() {
        Activity a = activity(Constants.ACTIVITY_SIGNING, FUTURE, FUTURE.plusHours(2), FUTURE, 10);
        a.setAuditStatus(Constants.AUDIT_PENDING);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(a);
        assertThatThrownBy(() -> service.signup(USER_ID, ACTIVITY_ID, new SignupDTO()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未通过审核");
    }

    @Test
    @DisplayName("未设报名截止且活动未开始 → 允许报名并入库")
    void signup_allowedWhenNoDeadlineAndNotStarted() {
        mockPassedActivity(activity(Constants.ACTIVITY_SIGNING, FUTURE, FUTURE.plusHours(2), null, 10));
        when(memberMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectById(USER_ID)).thenReturn(user());

        service.signup(USER_ID, ACTIVITY_ID, new SignupDTO());

        verify(memberMapper).insert(any(ActivityMember.class));
        verify(messageService, times(2)).send(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }
}
