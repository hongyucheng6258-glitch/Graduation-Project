package com.campus.platform.module.activity.service.impl;

import com.campus.platform.module.activity.service.IActivitySigninService;
import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.mapper.MapperQueries;
import com.campus.platform.module.activity.dto.SigninDTO;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.entity.ActivityMember;
import com.campus.platform.module.activity.entity.ActivitySignin;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.activity.mapper.ActivityMemberMapper;
import com.campus.platform.module.activity.mapper.ActivitySigninMapper;
import com.campus.platform.module.message.service.IMessageService;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.utils.SignTokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ActivitySigninServiceImpl implements IActivitySigninService {
    @Resource
    private ActivityMapper activityMapper;
    @Resource
    private ActivityMemberMapper memberMapper;
    @Resource
    private ActivitySigninMapper signinMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private IMessageService messageService;
    @Resource
    private SignTokenUtils signTokenUtils;

    public boolean signinOpen(Activity activity, LocalDateTime now) {
        return activity.getAuditStatus() == Constants.AUDIT_PASS
                && activity.getStatus() != Constants.ACTIVITY_OFF
                && activity.getStatus() != Constants.ACTIVITY_ENDED
                && activity.getStartTime() != null
                && !now.isBefore(activity.getStartTime())
                && (activity.getEndTime() == null || now.isBefore(activity.getEndTime()));
    }

    /** 发布者获取签到二维码内容（campus://signin/{id}/{token}） */
    @Override
    public String signinQrCode(Long userId, Long activityId) {
        requirePublisher(userId, activityId);
        return signTokenUtils.generateQrContent(activityId);
    }

    /**
     * 扫码签到：token 校验 + 必须为已通过报名的成员 + 防重复签到。
     */
    @Transactional
    @Override
    public void signin(Long userId, SigninDTO dto) {
        Long activityId = dto.getActivityId();
        if (!signTokenUtils.verify(activityId, dto.getToken())) {
            throw new BizException(ResultCode.BAD_REQUEST, "签到二维码无效");
        }
        Activity activity = activityMapper.selectForUpdate(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        if (!signinOpen(activity, LocalDateTime.now())) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅可在活动开始后、结束前签到");
        }
        ActivityMember member = memberMapper.selectOne(MapperQueries.limit(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getActivityId, activityId)
                .eq(ActivityMember::getUserId, userId), 1));
        if (member == null || member.getStatus() != Constants.MEMBER_APPROVED) {
            throw new BizException(ResultCode.FORBIDDEN, "报名未通过，无法签到");
        }
        long signinCount = signinMapper.selectCount(new LambdaQueryWrapper<ActivitySignin>()
                .eq(ActivitySignin::getActivityId, activityId)
                .eq(ActivitySignin::getUserId, userId));
        if (signinCount > 0) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "你已签到，请勿重复操作");
        }
        ActivitySignin signin = new ActivitySignin();
        signin.setActivityId(activityId);
        signin.setUserId(userId);
        signinMapper.insert(signin);
        // 通知发布者：有人已扫码签到
        User signer = userMapper.selectById(userId);
        messageService.send(activity.getUserId(), Constants.MSG_INTERACT,
                "成员已签到",
                String.format("「%s」已扫码签到你的活动「%s」。",
                        signer == null ? "有成员" : signer.getNickname(), activity.getTitle()),
                Constants.BIZ_ACTIVITY, activityId);
    }

    private void requirePublisher(Long userId, Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        if (!activity.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只有活动发布者可以执行此操作");
        }
    }
}
