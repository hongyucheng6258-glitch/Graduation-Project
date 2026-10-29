package com.campus.platform.module.admin.service;

import com.campus.platform.module.message.service.MessageService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.partner.entity.StudyPartner;
import com.campus.platform.module.partner.mapper.StudyPartnerMapper;
import com.campus.platform.module.post.entity.Post;
import com.campus.platform.module.post.mapper.PostMapper;
import com.campus.platform.module.qa.entity.CampusQuestion;
import com.campus.platform.module.qa.mapper.CampusQuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 通用审核服务（D2，架构设计 1.1 难点3）：
 * UGC（idle/activity/lostfound/post/partner）统一 audit_status 状态机；
 * 通过/驳回后消息通知作者。
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final IdleItemMapper idleItemMapper;
    private final ActivityMapper activityMapper;
    private final LostFoundMapper lostFoundMapper;
    private final PostMapper postMapper;
    private final StudyPartnerMapper studyPartnerMapper;
    private final CampusQuestionMapper questionMapper;
    private final MessageService messageService;

    /** 待审列表 */
    public PageResult<?> pendingList(String type, int pageNum, int pageSize) {
        return switch (type) {
            case Constants.BIZ_IDLE -> PageResult.of(idleItemMapper.selectPage(
                    new Page<>(pageNum, pageSize),
                    new LambdaQueryWrapper<IdleItem>()
                            .eq(IdleItem::getAuditStatus, Constants.AUDIT_PENDING)
                            .orderByAsc(IdleItem::getId)));
            case Constants.BIZ_ACTIVITY -> PageResult.of(activityMapper.selectPage(
                    new Page<>(pageNum, pageSize),
                    new LambdaQueryWrapper<Activity>()
                            .eq(Activity::getAuditStatus, Constants.AUDIT_PENDING)
                            .orderByAsc(Activity::getId)));
            case Constants.BIZ_LOSTFOUND -> PageResult.of(lostFoundMapper.selectPage(
                    new Page<>(pageNum, pageSize),
                    new LambdaQueryWrapper<LostFound>()
                            .eq(LostFound::getAuditStatus, Constants.AUDIT_PENDING)
                            .orderByAsc(LostFound::getId)));
            case Constants.BIZ_POST -> PageResult.of(postMapper.selectPage(
                    new Page<>(pageNum, pageSize),
                    new LambdaQueryWrapper<Post>()
                            .eq(Post::getAuditStatus, Constants.AUDIT_PENDING)
                            .orderByAsc(Post::getId)));
            case Constants.BIZ_PARTNER -> PageResult.of(studyPartnerMapper.selectPage(
                    new Page<>(pageNum, pageSize),
                    new LambdaQueryWrapper<StudyPartner>()
                            .eq(StudyPartner::getAuditStatus, Constants.AUDIT_PENDING)
                            .orderByAsc(StudyPartner::getId)));
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的审核类型: " + type);
        };
    }

    /** 全量内容列表：自动通过、人工通过、待审核、已驳回均可管理。 */
    public PageResult<?> allList(String type, int pageNum, int pageSize) {
        return switch (type) {
            case Constants.BIZ_IDLE -> PageResult.of(idleItemMapper.selectPage(
                    new Page<>(pageNum, pageSize), new LambdaQueryWrapper<IdleItem>().orderByDesc(IdleItem::getId)));
            case Constants.BIZ_ACTIVITY -> PageResult.of(activityMapper.selectPage(
                    new Page<>(pageNum, pageSize), new LambdaQueryWrapper<Activity>().orderByDesc(Activity::getId)));
            case Constants.BIZ_LOSTFOUND -> PageResult.of(lostFoundMapper.selectPage(
                    new Page<>(pageNum, pageSize), new LambdaQueryWrapper<LostFound>().orderByDesc(LostFound::getId)));
            case Constants.BIZ_POST -> PageResult.of(postMapper.selectPage(
                    new Page<>(pageNum, pageSize), new LambdaQueryWrapper<Post>().orderByDesc(Post::getId)));
            case Constants.BIZ_PARTNER -> PageResult.of(studyPartnerMapper.selectPage(
                    new Page<>(pageNum, pageSize), new LambdaQueryWrapper<StudyPartner>().orderByDesc(StudyPartner::getId)));
            case Constants.BIZ_QA -> PageResult.of(questionMapper.selectPage(
                    new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CampusQuestion>().orderByDesc(CampusQuestion::getId)));
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的内容类型: " + type);
        };
    }

    /** 审核通过 → 通知作者 */
    public void pass(String type, Long id) {
        AuditedTarget target = doAudit(type, id, Constants.AUDIT_PASS, null);
        notifyAuthor(target, true, null);
    }

    /** 审核驳回（必填理由）→ 通知作者 */
    public void reject(String type, Long id, String reason) {
        AuditedTarget target = doAudit(type, id, Constants.AUDIT_REJECT, reason);
        notifyAuthor(target, false, reason);
    }

    /** 各类型统一更新审核状态，返回作者与可回跳的业务目标 */
    private AuditedTarget doAudit(String type, Long id, int auditStatus, String reason) {
        switch (type) {
            case Constants.BIZ_IDLE -> {
                IdleItem item = idleItemMapper.selectById(id);
                if (item == null) {
                    throw new BizException(ResultCode.NOT_FOUND, "内容不存在");
                }
                item.setAuditStatus(auditStatus);
                item.setAuditReason(reason);
                item.setAuditSource(item.getAiRiskLevel() == null ? "manual" : "ai_manual");
                idleItemMapper.updateById(item);
                return new AuditedTarget(item.getUserId(), "闲置「" + item.getTitle() + "」", Constants.BIZ_IDLE, item.getId());
            }
            case Constants.BIZ_ACTIVITY -> {
                Activity activity = activityMapper.selectById(id);
                if (activity == null) {
                    throw new BizException(ResultCode.NOT_FOUND, "内容不存在");
                }
                activity.setAuditStatus(auditStatus);
                activity.setAuditReason(reason);
                activity.setAuditSource(activity.getAiRiskLevel() == null ? "manual" : "ai_manual");
                activityMapper.updateById(activity);
                return new AuditedTarget(activity.getUserId(), "活动「" + activity.getTitle() + "」", Constants.BIZ_ACTIVITY, activity.getId());
            }
            case Constants.BIZ_LOSTFOUND -> {
                LostFound lf = lostFoundMapper.selectById(id);
                if (lf == null) {
                    throw new BizException(ResultCode.NOT_FOUND, "内容不存在");
                }
                lf.setAuditStatus(auditStatus);
                lf.setAuditReason(reason);
                lf.setAuditSource(lf.getAiRiskLevel() == null ? "manual" : "ai_manual");
                lostFoundMapper.updateById(lf);
                return new AuditedTarget(lf.getUserId(), "失物招领「" + lf.getTitle() + "」", Constants.BIZ_LOSTFOUND, lf.getId());
            }
            case Constants.BIZ_POST -> {
                Post post = postMapper.selectById(id);
                if (post == null) {
                    throw new BizException(ResultCode.NOT_FOUND, "内容不存在");
                }
                post.setAuditStatus(auditStatus);
                post.setAuditReason(reason);
                post.setAuditSource(post.getAiRiskLevel() == null ? "manual" : "ai_manual");
                postMapper.updateById(post);
                String preview = post.getContent().length() > 20
                        ? post.getContent().substring(0, 20) + "..." : post.getContent();
                return new AuditedTarget(post.getUserId(), "动态「" + preview + "」", Constants.BIZ_POST, post.getId());
            }
            case Constants.BIZ_PARTNER -> {
                StudyPartner p = studyPartnerMapper.selectById(id);
                if (p == null) {
                    throw new BizException(ResultCode.NOT_FOUND, "内容不存在");
                }
                p.setAuditStatus(auditStatus);
                p.setAuditReason(reason);
                p.setAuditSource(p.getAiRiskLevel() == null ? "manual" : "ai_manual");
                studyPartnerMapper.updateById(p);
                return new AuditedTarget(p.getUserId(), "学习搭子「" + p.getSubject() + "」", Constants.BIZ_PARTNER, p.getId());
            }
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的审核类型: " + type);
        }
    }

    private void notifyAuthor(AuditedTarget target, boolean passed, String reason) {
        messageService.send(target.authorId(), Constants.MSG_AUDIT,
                passed ? "审核通过" : "审核未通过",
                passed ? "你发布的" + target.title() + "已通过审核，现已公开展示。"
                        : "你发布的" + target.title() + "未通过审核，原因：" + reason,
                target.bizType(), target.bizId());
    }

    private record AuditedTarget(Long authorId, String title, String bizType, Long bizId) {
    }
}
