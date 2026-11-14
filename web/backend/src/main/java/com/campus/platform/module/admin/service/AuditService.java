package com.campus.platform.module.admin.service;

import com.campus.platform.module.message.service.MessageService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

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
        return pendingList(type, pageNum, pageSize, false);
    }

    public PageResult<?> pendingList(String type, int pageNum, int pageSize, boolean riskOnly) {
        return switch (type) {
            case Constants.BIZ_IDLE -> PageResult.of(idleItemMapper.selectPage(
                    new Page<>(pageNum, pageSize),
                    new LambdaQueryWrapper<IdleItem>()
                            .eq(IdleItem::getAuditStatus, Constants.AUDIT_PENDING)
                            .ge(riskOnly, IdleItem::getAiRiskLevel, 1)
                            .orderByAsc(IdleItem::getId)));
            case Constants.BIZ_ACTIVITY -> PageResult.of(activityMapper.selectPage(
                    new Page<>(pageNum, pageSize),
                    new LambdaQueryWrapper<Activity>()
                            .eq(Activity::getAuditStatus, Constants.AUDIT_PENDING)
                            .ge(riskOnly, Activity::getAiRiskLevel, 1)
                            .orderByAsc(Activity::getId)));
            case Constants.BIZ_LOSTFOUND -> PageResult.of(lostFoundMapper.selectPage(
                    new Page<>(pageNum, pageSize),
                    new LambdaQueryWrapper<LostFound>()
                            .eq(LostFound::getAuditStatus, Constants.AUDIT_PENDING)
                            .ge(riskOnly, LostFound::getAiRiskLevel, 1)
                            .orderByAsc(LostFound::getId)));
            case Constants.BIZ_POST -> PageResult.of(postMapper.selectPage(
                    new Page<>(pageNum, pageSize),
                    new LambdaQueryWrapper<Post>()
                            .eq(Post::getAuditStatus, Constants.AUDIT_PENDING)
                            .ge(riskOnly, Post::getAiRiskLevel, 1)
                            .orderByAsc(Post::getId)));
            case Constants.BIZ_PARTNER -> PageResult.of(studyPartnerMapper.selectPage(
                    new Page<>(pageNum, pageSize),
                    new LambdaQueryWrapper<StudyPartner>()
                            .eq(StudyPartner::getAuditStatus, Constants.AUDIT_PENDING)
                            .ge(riskOnly, StudyPartner::getAiRiskLevel, 1)
                            .orderByAsc(StudyPartner::getId)));
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的审核类型: " + type);
        };
    }

    public IdleItem idleDetail(Long id) {
        var item = idleItemMapper.selectById(id);
        if (item == null) throw new BizException(ResultCode.NOT_FOUND, "未找到闲置记录");
        return item;
    }

    public Activity activityDetail(Long id) {
        var activity = activityMapper.selectById(id);
        if (activity == null) throw new BizException(ResultCode.NOT_FOUND, "未找到活动记录");
        return activity;
    }

    /** 全量内容列表：自动通过、人工通过、待审核、已驳回均可管理。 */
    public PageResult<?> allList(String type, String keyword, Long record, int pageNum, int pageSize) {
        return switch (type) {
            case Constants.BIZ_IDLE -> searchList(idleItemMapper, pageNum, pageSize, keyword, record,
                    IdleItem::getId, IdleItem::getTitle, IdleItem::getDescription);
            case Constants.BIZ_ACTIVITY -> searchList(activityMapper, pageNum, pageSize, keyword, record,
                    Activity::getId, Activity::getTitle, Activity::getDescription);
            case Constants.BIZ_LOSTFOUND -> searchList(lostFoundMapper, pageNum, pageSize, keyword, record,
                    LostFound::getId, LostFound::getTitle, LostFound::getDescription);
            case Constants.BIZ_POST -> searchList(postMapper, pageNum, pageSize, keyword, record,
                    Post::getId, Post::getContent);
            case Constants.BIZ_PARTNER -> searchList(studyPartnerMapper, pageNum, pageSize, keyword, record,
                    StudyPartner::getId, StudyPartner::getSubject, StudyPartner::getGoal, StudyPartner::getIntro);
            case Constants.BIZ_QA -> searchList(questionMapper, pageNum, pageSize, keyword, record,
                    CampusQuestion::getId, CampusQuestion::getTitle, CampusQuestion::getContent);
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的内容类型: " + type);
        };
    }

    @SafeVarargs
    private <T> PageResult<T> searchList(BaseMapper<T> mapper, int pageNum, int pageSize,
                                       String keyword, Long record, SFunction<T, Long> id,
                                       SFunction<T, String> firstField, SFunction<T, String>... otherFields) {
        LambdaQueryWrapper<T> query = new LambdaQueryWrapper<T>().eq(record != null, id, record);
        if (keyword != null && !keyword.isBlank()) {
            String text = keyword.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            query.and(fields -> {
                fields.like(firstField, text);
                for (SFunction<T, String> field : otherFields) {
                    fields.or().like(field, text);
                }
            });
        }
        return PageResult.of(mapper.selectPage(new Page<>(pageNum, pageSize), query.orderByDesc(id)));
    }

    /** 审核通过 → 通知作者 */
    @Transactional
    public void pass(String type, Long id) {
        pass(type, id, false);
    }

    @Transactional
    public void pass(String type, Long id, boolean pendingOnly) {
        if (Constants.BIZ_IDLE.equals(type)) {
            reviewIdle(id, Constants.AUDIT_PASS, null, pendingOnly);
            return;
        }
        if (Constants.BIZ_ACTIVITY.equals(type)) {
            reviewActivity(id, Constants.AUDIT_PASS, null, pendingOnly);
            return;
        }
        AuditedTarget target = doAudit(type, id, Constants.AUDIT_PASS, null, pendingOnly);
        notifyAuthor(target, true, null);
    }

    /** 审核驳回（必填理由）→ 通知作者 */
    @Transactional
    public void reject(String type, Long id, String reason) {
        reject(type, id, reason, false);
    }

    @Transactional
    public void reject(String type, Long id, String reason, boolean pendingOnly) {
        if (Constants.BIZ_IDLE.equals(type)) {
            reviewIdle(id, Constants.AUDIT_REJECT, reason, pendingOnly);
            return;
        }
        if (Constants.BIZ_ACTIVITY.equals(type)) {
            reviewActivity(id, Constants.AUDIT_REJECT, reason, pendingOnly);
            return;
        }
        AuditedTarget target = doAudit(type, id, Constants.AUDIT_REJECT, reason, pendingOnly);
        notifyAuthor(target, false, reason);
    }

    @Transactional
    public int reviewPendingIdle(Long id, boolean pass, String reason) {
        int decision = pass ? Constants.AUDIT_PASS : Constants.AUDIT_REJECT;
        reviewIdle(id, decision, pass ? null : reason, true);
        return decision;
    }

    @Transactional
    public int reviewPendingActivity(Long id, boolean pass, String reason) {
        int decision = pass ? Constants.AUDIT_PASS : Constants.AUDIT_REJECT;
        reviewActivity(id, decision, pass ? null : reason, true);
        return decision;
    }

    private void reviewActivity(Long id, int decision, String reason, boolean pendingOnly) {
        var activity = activityMapper.selectForUpdate(id);
        if (activity == null) throw new BizException(ResultCode.NOT_FOUND, "内容不存在");
        if (pendingOnly && !Objects.equals(activity.getAuditStatus(), Constants.AUDIT_PENDING)) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该活动已处理，请刷新");
        }
        if (Objects.equals(activity.getAuditStatus(), decision) && Objects.equals(activity.getAuditReason(), reason)) return;
        String source = activity.getAiRiskLevel() == null ? "manual" : "ai_manual";
        activityMapper.updateAuditDecision(id, decision, reason, source);
        notifyAuthor(new AuditedTarget(activity.getUserId(), "活动「" + activity.getTitle() + "」", Constants.BIZ_ACTIVITY, id),
                decision == Constants.AUDIT_PASS, reason);
    }

    private void reviewIdle(Long id, int decision, String reason, boolean pendingOnly) {
        var item = idleItemMapper.lockForAudit(id);
        if (item == null) throw new BizException(ResultCode.NOT_FOUND, "内容不存在");
        if (pendingOnly && !Objects.equals(item.getAuditStatus(), Constants.AUDIT_PENDING)) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该闲置已处理，请刷新");
        }
        if (Objects.equals(item.getAuditStatus(), decision) && Objects.equals(item.getAuditReason(), reason)) return;
        String source = item.getAiRiskLevel() == null ? "manual" : "ai_manual";
        idleItemMapper.updateAuditDecision(id, decision, reason, source);
        notifyAuthor(new AuditedTarget(item.getUserId(), "闲置「" + item.getTitle() + "」", Constants.BIZ_IDLE, id),
                decision == Constants.AUDIT_PASS, reason);
    }

    /** 各类型统一更新审核状态，返回作者与可回跳的业务目标 */
    private AuditedTarget doAudit(String type, Long id, int auditStatus, String reason, boolean pendingOnly) {
        switch (type) {
            case Constants.BIZ_LOSTFOUND -> {
                LostFound lf = pendingOnly
                        ? lostFoundMapper.selectOne(new LambdaQueryWrapper<LostFound>().eq(LostFound::getId, id).last("FOR UPDATE"))
                        : lostFoundMapper.selectById(id);
                if (lf == null) {
                    throw new BizException(ResultCode.NOT_FOUND, "内容不存在");
                }
                requirePending(lf.getAuditStatus(), pendingOnly);
                lf.setAuditStatus(auditStatus);
                lf.setAuditReason(reason);
                lf.setAuditSource(lf.getAiRiskLevel() == null ? "manual" : "ai_manual");
                lostFoundMapper.updateById(lf);
                return new AuditedTarget(lf.getUserId(), "失物招领「" + lf.getTitle() + "」", Constants.BIZ_LOSTFOUND, lf.getId());
            }
            case Constants.BIZ_POST -> {
                Post post = pendingOnly
                        ? postMapper.selectOne(new LambdaQueryWrapper<Post>().eq(Post::getId, id).last("FOR UPDATE"))
                        : postMapper.selectById(id);
                if (post == null) {
                    throw new BizException(ResultCode.NOT_FOUND, "内容不存在");
                }
                requirePending(post.getAuditStatus(), pendingOnly);
                post.setAuditStatus(auditStatus);
                post.setAuditReason(reason);
                post.setAuditSource(post.getAiRiskLevel() == null ? "manual" : "ai_manual");
                postMapper.updateById(post);
                String preview = post.getContent().length() > 20
                        ? post.getContent().substring(0, 20) + "..." : post.getContent();
                return new AuditedTarget(post.getUserId(), "动态「" + preview + "」", Constants.BIZ_POST, post.getId());
            }
            case Constants.BIZ_PARTNER -> {
                StudyPartner p = pendingOnly
                        ? studyPartnerMapper.selectOne(new LambdaQueryWrapper<StudyPartner>().eq(StudyPartner::getId, id).last("FOR UPDATE"))
                        : studyPartnerMapper.selectById(id);
                if (p == null) {
                    throw new BizException(ResultCode.NOT_FOUND, "内容不存在");
                }
                requirePending(p.getAuditStatus(), pendingOnly);
                p.setAuditStatus(auditStatus);
                p.setAuditReason(reason);
                p.setAuditSource(p.getAiRiskLevel() == null ? "manual" : "ai_manual");
                studyPartnerMapper.updateById(p);
                return new AuditedTarget(p.getUserId(), "学习搭子「" + p.getSubject() + "」", Constants.BIZ_PARTNER, p.getId());
            }
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的审核类型: " + type);
        }
    }

    private void requirePending(Integer status, boolean pendingOnly) {
        if (pendingOnly && !Objects.equals(status, Constants.AUDIT_PENDING)) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该内容已处理，请刷新");
        }
    }

    private void notifyAuthor(AuditedTarget target, boolean passed, String reason) {
        String passedResult = Constants.BIZ_IDLE.equals(target.bizType())
                ? "已通过审核。可在我的闲置查看展示状态。" : "已通过审核，现已公开展示。";
        messageService.send(target.authorId(), Constants.MSG_AUDIT,
                passed ? "审核通过" : "审核未通过",
                passed ? "你发布的" + target.title() + passedResult
                        : "你发布的" + target.title() + "未通过审核，原因：" + reason,
                target.bizType(), target.bizId());
    }

    private record AuditedTarget(Long authorId, String title, String bizType, Long bizId) {
    }
}
