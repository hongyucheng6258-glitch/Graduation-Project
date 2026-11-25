package com.campus.platform.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.PageResult;
import com.campus.platform.module.admin.entity.AdminOperationLog;
import com.campus.platform.module.admin.mapper.AdminOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

/**
 * AdminOperationLogService 服务接口。
 */
public interface IAdminOperationLogService {

    void record(AdminOperationLog entry);

    PageResult<AdminOperationLog> list(String username, String module, Long targetId, Boolean succeeded, LocalDateTime from, LocalDateTime to, int pageNum, int pageSize);
}
