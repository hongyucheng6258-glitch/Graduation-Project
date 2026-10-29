package com.campus.platform.module.admin.service;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.entity.ActivityMember;
import com.campus.platform.module.activity.entity.ActivitySignin;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.activity.mapper.ActivityMemberMapper;
import com.campus.platform.module.activity.mapper.ActivitySigninMapper;
import com.campus.platform.module.admin.vo.SigninMemberVO;
import com.campus.platform.module.admin.vo.SigninReportVO;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.partner.entity.StudyPartner;
import com.campus.platform.module.partner.mapper.StudyPartnerMapper;
import com.campus.platform.module.qa.entity.CampusQuestion;
import com.campus.platform.module.qa.mapper.CampusQuestionMapper;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端内容管理服务：已通过内容下架/恢复、活动签到报表。
 */
@Service
@RequiredArgsConstructor
public class AdminContentService {

    private final ActivityMapper activityMapper;
    private final IdleItemMapper idleItemMapper;
    private final LostFoundMapper lostFoundMapper;
    private final StudyPartnerMapper studyPartnerMapper;
    private final CampusQuestionMapper campusQuestionMapper;
    private final ActivityMemberMapper memberMapper;
    private final ActivitySigninMapper signinMapper;
    private final UserMapper userMapper;

    /** 下架（type: activity/idle/lostfound/partner/qa） */
    public void offline(String type, Long id) {
        switch (type) {
            case "activity":
                setActivityStatus(id, Constants.ACTIVITY_OFF);
                break;
            case "idle":
                setIdleStatus(id, Constants.IDLE_OFF_SHELF);
                break;
            case "lostfound":
                setLfStatus(id, Constants.LF_OFF);
                break;
            case "partner":
                setPartnerStatus(id, Constants.PARTNER_OFF);
                break;
            case "qa":
                setQaStatus(id, Constants.QA_OFF);
                break;
            default:
                throw new BizException(ResultCode.BAD_REQUEST, "不支持的内容类型");
        }
    }

    /** 恢复上架（仅审核通过内容） */
    public void online(String type, Long id) {
        switch (type) {
            case "activity":
                setActivityStatus(id, Constants.ACTIVITY_SIGNING);
                break;
            case "idle":
                setIdleStatus(id, Constants.IDLE_ON_SHELF);
                break;
            case "lostfound":
                setLfStatus(id, Constants.LF_DOING);
                break;
            case "partner":
                setPartnerStatus(id, Constants.PARTNER_MATCHING);
                break;
            case "qa":
                setQaStatus(id, Constants.QA_OPEN);
                break;
            default:
                throw new BizException(ResultCode.BAD_REQUEST, "不支持的内容类型");
        }
    }

    /** 活动签到报表 */
    public SigninReportVO signinReport(Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        List<ActivityMember> members = memberMapper.selectList(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getActivityId, activityId)
                .orderByAsc(ActivityMember::getId));
        List<ActivitySignin> signs = signinMapper.selectList(new LambdaQueryWrapper<ActivitySignin>()
                .eq(ActivitySignin::getActivityId, activityId));

        Map<Long, ActivitySignin> signMap = new HashMap<>();
        for (ActivitySignin s : signs) {
            signMap.putIfAbsent(s.getUserId(), s);
        }

        List<SigninMemberVO> rows = new ArrayList<>();
        int joined = 0;
        for (ActivityMember m : members) {
            SigninMemberVO vo = new SigninMemberVO();
            vo.setUserId(m.getUserId());
            User u = userMapper.selectById(m.getUserId());
            vo.setNickname(u == null ? "" : u.getNickname());
            vo.setMemberStatus(m.getStatus());
            if (m.getStatus() != null && m.getStatus() == Constants.MEMBER_APPROVED) {
                joined++;
            }
            ActivitySignin s = signMap.get(m.getUserId());
            vo.setSigned(s != null);
            vo.setSignTime(s == null ? null : s.getSignTime());
            rows.add(vo);
        }

        int signedCount = (int) rows.stream().filter(SigninMemberVO::getSigned).count();
        SigninReportVO vo = new SigninReportVO();
        vo.setActivityId(activityId);
        vo.setTitle(activity.getTitle());
        vo.setJoinedCount(joined);
        vo.setSigninCount(signedCount);
        vo.setSigninRate(joined == 0 ? 0.0 : Math.round(signedCount * 10000.0 / joined) / 100.0);
        vo.setMembers(rows);
        return vo;
    }

    private void setActivityStatus(Long id, int status) {
        Activity a = activityMapper.selectById(id);
        if (a == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        a.setStatus(status);
        activityMapper.updateById(a);
    }

    private void setIdleStatus(Long id, int status) {
        IdleItem item = idleItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ResultCode.NOT_FOUND, "闲置物品不存在");
        }
        if (item.getAuditStatus() != Constants.AUDIT_PASS) {
            throw new BizException(ResultCode.AUDIT_PENDING, "内容未通过审核");
        }
        item.setStatus(status);
        idleItemMapper.updateById(item);
    }

    private void setLfStatus(Long id, int status) {
        LostFound lf = lostFoundMapper.selectById(id);
        if (lf == null) {
            throw new BizException(ResultCode.NOT_FOUND, "失物信息不存在");
        }
        if (lf.getAuditStatus() != Constants.AUDIT_PASS) {
            throw new BizException(ResultCode.AUDIT_PENDING, "内容未通过审核");
        }
        lf.setStatus(status);
        lostFoundMapper.updateById(lf);
    }

    private void setPartnerStatus(Long id, int status) {
        StudyPartner p = studyPartnerMapper.selectById(id);
        if (p == null) {
            throw new BizException(ResultCode.NOT_FOUND, "学习搭子不存在");
        }
        if (p.getAuditStatus() != Constants.AUDIT_PASS) {
            throw new BizException(ResultCode.AUDIT_PENDING, "内容未通过审核");
        }
        p.setStatus(status);
        studyPartnerMapper.updateById(p);
    }

    private void setQaStatus(Long id, int status) {
        CampusQuestion q = campusQuestionMapper.selectById(id);
        if (q == null) {
            throw new BizException(ResultCode.NOT_FOUND, "问题不存在");
        }
        q.setStatus(status);
        campusQuestionMapper.updateById(q);
    }
}
