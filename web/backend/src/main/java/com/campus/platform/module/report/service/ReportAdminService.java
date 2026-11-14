package com.campus.platform.module.report.service;

import com.campus.platform.module.report.dto.ReportHandleDTO;
import com.campus.platform.module.report.mapper.ReportMapper;
import com.campus.platform.module.report.entity.Report;

import com.campus.platform.module.message.service.MessageService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.post.entity.Post;
import com.campus.platform.module.post.entity.PostComment;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.post.mapper.PostCommentMapper;
import com.campus.platform.module.post.mapper.PostMapper;
import com.campus.platform.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 管理端-举报处置闭环（D3）：处置（下架/警告/封禁）→ 通知举报人。
 */
@Service
@RequiredArgsConstructor
public class ReportAdminService {

    private final ReportMapper reportMapper;
    private final IdleItemMapper idleItemMapper;
    private final ActivityMapper activityMapper;
    private final LostFoundMapper lostFoundMapper;
    private final PostMapper postMapper;
    private final PostCommentMapper postCommentMapper;
    private final UserMapper userMapper;
    private final com.campus.platform.module.chat.mapper.ChatMessageMapper chatMessageMapper;
    private final com.campus.platform.module.admin.service.AdminUserService adminUserService;
    private final com.campus.platform.module.admin.service.AdminContentService adminContentService;
    private final MessageService messageService;

    /** 举报列表（状态筛选） */
    public PageResult<Report> list(Integer status, Long record, int pageNum, int pageSize) {
        Page<Report> page = reportMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Report>()
                        .eq(status != null, Report::getStatus, status)
                        .eq(record != null, Report::getId, record)
                        .orderByDesc(Report::getId));
        return PageResult.of(page);
    }

    public Report detail(Long id) {
        Report report = reportMapper.selectById(id);
        if (report == null) throw new BizException(ResultCode.NOT_FOUND, "举报不存在");
        return report;
    }

    /**
     * 处置举报。
     * action: offline=下架内容 / warn=警告发布者 / ban=封禁发布者 / ignore=举报不成立
     */
    @Transactional
    public void handle(Long adminId, Long reportId, ReportHandleDTO dto) {
        Report report = reportMapper.lockById(reportId);
        if (report == null) throw new BizException(ResultCode.NOT_FOUND, "举报不存在");
        if (report.getStatus() == Constants.REPORT_HANDLED) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该举报已处理");
        }
        boolean isUserReport = Constants.BIZ_USER.equals(report.getTargetType());
        switch (dto.getAction()) {
            case "offline" -> {
                if (isUserReport) {
                    throw new BizException(ResultCode.BAD_REQUEST, "用户举报不支持下架操作");
                }
                offlineTarget(report.getTargetType(), report.getTargetId());
            }
            case "warn" -> warnTarget(report.getTargetType(), report.getTargetId(), dto.getHandleResult());
            case "ban" -> banAuthor(report.getTargetType(), report.getTargetId());
            case "ignore" -> { /* 举报不成立，仅关闭 */ }
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的处置动作");
        }
        report.setStatus(Constants.REPORT_HANDLED);
        report.setHandleResult(dto.getHandleResult());
        report.setHandlerId(adminId);
        report.setHandleTime(LocalDateTime.now());
        reportMapper.updateById(report);
        // 通知举报人处置结果
        messageService.send(report.getReporterId(), Constants.MSG_AUDIT,
                "举报处理结果",
                (isUserReport ? "你举报的用户已处理完毕：" : "你举报的内容已处理完毕：") + dto.getHandleResult(),
                "report", reportId);
    }

    /** 按类型下架目标内容 */
    private void offlineTarget(String type, Long targetId) {
        switch (type) {
            case Constants.BIZ_IDLE, Constants.BIZ_ACTIVITY, Constants.BIZ_LOSTFOUND ->
                    adminContentService.offline(type, targetId);
            case Constants.BIZ_POST -> {
                Post post = postMapper.selectById(targetId);
                if (post != null) {
                    post.setAuditStatus(Constants.AUDIT_REJECT);
                    post.setAuditReason("举报处置下架");
                    postMapper.updateById(post);
                }
            }
            case "comment" -> {
                PostComment comment = postCommentMapper.selectById(targetId);
                if (comment != null) {
                    comment.setStatus(Constants.COMMENT_HIDDEN);
                    postCommentMapper.updateById(comment);
                }
            }
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的举报类型");
        }
    }

    private void warnTarget(String type, Long targetId, String handleResult) {
        Long authorId = requireAuthor(type, targetId);
        messageService.send(authorId, Constants.MSG_SYSTEM, "账号警告通知",
                "你因违反校园平台规范被举报并核实，本次予以警告：" + handleResult,
                null, null);
    }

    private void banAuthor(String type, Long targetId) {
        Long authorId = requireAuthor(type, targetId);
        adminUserService.updateStatus(authorId, Constants.USER_STATUS_BANNED);
        messageService.send(authorId, Constants.MSG_SYSTEM, "账号处罚通知",
                "因违规行为被举报核实，你的账号已被封禁，如有异议请联系管理员。",
                null, null);
    }

    private Long requireAuthor(String type, Long targetId) {
        Long authorId = switch (type) {
            case Constants.BIZ_IDLE -> {
                IdleItem item = idleItemMapper.selectById(targetId);
                yield item == null ? null : item.getUserId();
            }
            case Constants.BIZ_ACTIVITY -> {
                Activity activity = activityMapper.selectById(targetId);
                yield activity == null ? null : activity.getUserId();
            }
            case Constants.BIZ_LOSTFOUND -> {
                LostFound lf = lostFoundMapper.selectById(targetId);
                yield lf == null ? null : lf.getUserId();
            }
            case Constants.BIZ_POST -> {
                Post post = postMapper.selectById(targetId);
                yield post == null ? null : post.getUserId();
            }
            case "comment" -> {
                PostComment comment = postCommentMapper.selectById(targetId);
                yield comment == null ? null : comment.getUserId();
            }
            case "chat_message" -> {
                var message = chatMessageMapper.selectById(targetId);
                yield message == null ? null : message.getSenderId();
            }
            case Constants.BIZ_USER -> targetId;
            default -> null;
        };
        if (authorId == null || userMapper.selectById(authorId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "举报对象或发布者不存在");
        }
        return authorId;
    }
}
