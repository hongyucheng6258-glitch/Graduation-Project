package com.campus.platform.module.report.service.impl;

import com.campus.platform.module.report.mapper.ReportMapper;
import com.campus.platform.module.report.service.IReportService;
import com.campus.platform.module.report.dto.ReportDTO;
import com.campus.platform.module.report.entity.Report;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.PageResult;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.post.entity.Post;
import com.campus.platform.module.post.entity.PostComment;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.post.mapper.PostCommentMapper;
import com.campus.platform.module.post.mapper.PostMapper;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.module.message.service.IMessageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 举报服务（D3 学生发起侧）：校验举报目标存在后落库，等待管理端处置。
 */
@Service
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements IReportService {

    @Resource
    private IdleItemMapper idleItemMapper;

    @Resource
    private ActivityMapper activityMapper;

    @Resource
    private LostFoundMapper lostFoundMapper;

    @Resource
    private PostMapper postMapper;

    @Resource
    private PostCommentMapper postCommentMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private IMessageService messageService;

    /** 发起举报 */
    @Override
    public Report submit(Long userId, ReportDTO dto) {
        checkTargetExists(dto.getTargetType(), dto.getTargetId());
        Report report = new Report();
        report.setReporterId(userId);
        report.setTargetType(dto.getTargetType());
        report.setTargetId(dto.getTargetId());
        report.setReasonType(dto.getReasonType());
        report.setReason(dto.getReason());
        report.setStatus(Constants.REPORT_PENDING);
        baseMapper.insert(report);
        // 确认举报已提交
        messageService.send(userId, Constants.MSG_SYSTEM,
                "举报已提交",
                "你的举报已提交，平台将尽快核实处理，处理结果将通过消息通知你。",
                "report", report.getId());
        return report;
    }

    /** 当前学生提交的举报记录 */
    @Override
    public PageResult<Report> myList(Long userId, int pageNum, int pageSize) {
        Page<Report> page = baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getReporterId, userId)
                        .orderByDesc(Report::getId));
        return PageResult.of(page);
    }

    /** 校验举报目标真实存在 */
    private void checkTargetExists(String targetType, Long targetId) {
        Object target = switch (targetType) {
            case Constants.BIZ_IDLE -> idleItemMapper.selectById(targetId);
            case Constants.BIZ_ACTIVITY -> activityMapper.selectById(targetId);
            case Constants.BIZ_LOSTFOUND -> lostFoundMapper.selectById(targetId);
            case Constants.BIZ_POST -> postMapper.selectById(targetId);
            case Constants.BIZ_USER -> userMapper.selectById(targetId);
            case "comment" -> postCommentMapper.selectById(targetId);
            default -> null;
        };
        if (target == null) {
            throw new BizException(ResultCode.NOT_FOUND, "举报对象不存在");
        }
    }
}
