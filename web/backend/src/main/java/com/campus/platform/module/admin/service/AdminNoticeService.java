package com.campus.platform.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.notice.dto.NoticeSaveDTO;
import com.campus.platform.module.notice.entity.Notice;
import com.campus.platform.module.notice.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 管理端-公告管理（D4）：Markdown 编辑、发布/下线，发布后三端即时可见。
 */
@Service
@RequiredArgsConstructor
public class AdminNoticeService {

    private final NoticeMapper noticeMapper;

    /** 公告管理列表（全部状态） */
    public PageResult<Notice> list(Integer status, int pageNum, int pageSize) {
        Page<Notice> page = noticeMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Notice>()
                        .eq(status != null, Notice::getStatus, status)
                        .orderByDesc(Notice::getId));
        return PageResult.of(page);
    }

    public Notice detail(Long id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BizException(ResultCode.NOT_FOUND, "公告不存在");
        }
        return notice;
    }

    /** 新建（默认草稿） */
    public Notice create(Long adminId, NoticeSaveDTO dto) {
        Notice notice = new Notice();
        notice.setAdminId(adminId);
        notice.setTitle(dto.getTitle());
        notice.setContent(dto.getContent());
        notice.setCover(dto.getCover());
        notice.setStatus(Constants.NOTICE_DRAFT);
        noticeMapper.insert(notice);
        return notice;
    }

    /** 编辑 */
    public Notice update(Long id, NoticeSaveDTO dto) {
        Notice notice = detail(id);
        notice.setTitle(dto.getTitle());
        notice.setContent(dto.getContent());
        notice.setCover(dto.getCover());
        noticeMapper.updateById(notice);
        return notice;
    }

    /** 发布（记录发布时间） */
    public void publish(Long id) {
        Notice notice = detail(id);
        notice.setStatus(Constants.NOTICE_PUBLISHED);
        notice.setPublishTime(LocalDateTime.now());
        noticeMapper.updateById(notice);
    }

    /** 下线 */
    public void offline(Long id) {
        Notice notice = detail(id);
        notice.setStatus(Constants.NOTICE_OFFLINE);
        noticeMapper.updateById(notice);
    }

    /** 删除（物理删除） */
    public void delete(Long id) {
        detail(id);
        noticeMapper.deleteById(id);
    }
}
