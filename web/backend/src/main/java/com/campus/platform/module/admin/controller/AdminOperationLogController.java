package com.campus.platform.module.admin.controller;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.R;
import com.campus.platform.module.admin.audit.AdminPermission;
import com.campus.platform.module.admin.entity.AdminOperationLog;
import com.campus.platform.module.admin.service.AdminOperationLogService;
import com.campus.platform.module.admin.service.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/system/operation-log")
@RequiredArgsConstructor
public class AdminOperationLogController {
    private final AdminPermissionService permissions;
    private final AdminOperationLogService logs;

    @GetMapping
    public R<PageResult<AdminOperationLog>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Boolean succeeded,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        permissions.require(AdminPermission.OPERATION_LOG_READ);
        return R.ok(logs.list(username, module, targetId, succeeded, from, to, pageNum, pageSize));
    }
}
