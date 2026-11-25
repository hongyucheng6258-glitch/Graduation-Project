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
 * AdminNoticeService 服务接口。
 */
public interface IAdminNoticeService {

    PageResult<Notice> list(Integer status, int pageNum, int pageSize);

    Notice detail(Long id);

    Notice create(Long adminId, NoticeSaveDTO dto);

    Notice update(Long id, NoticeSaveDTO dto);

    void publish(Long id);

    void offline(Long id);

    void delete(Long id);
}
