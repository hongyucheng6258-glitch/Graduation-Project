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

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOperationLogService {
    private final AdminOperationLogMapper mapper;

    public void record(AdminOperationLog entry) {
        try {
            mapper.insert(entry);
        } catch (RuntimeException failure) {
            log.warn("Admin operation log write failed: module={}, action={}, targetId={}, cause={}",
                    entry.getModule(), entry.getAction(), entry.getTargetId(), failure.getClass().getSimpleName());
        }
    }

    public PageResult<AdminOperationLog> list(String username, String module, Long targetId, Boolean succeeded,
                                              LocalDateTime from, LocalDateTime to, int pageNum, int pageSize) {
        var query = new LambdaQueryWrapper<AdminOperationLog>()
                .eq(username != null && !username.isBlank(), AdminOperationLog::getAdminUsername, username)
                .eq(module != null && !module.isBlank(), AdminOperationLog::getModule, module)
                .eq(targetId != null, AdminOperationLog::getTargetId, targetId)
                .eq(succeeded != null, AdminOperationLog::isSucceeded, succeeded)
                .ge(from != null, AdminOperationLog::getCreateTime, from)
                .le(to != null, AdminOperationLog::getCreateTime, to)
                .orderByDesc(AdminOperationLog::getId);
        return PageResult.of(mapper.selectPage(new Page<>(Math.max(1, pageNum), Math.max(1, Math.min(100, pageSize))), query));
    }
}
