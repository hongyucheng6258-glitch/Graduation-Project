package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.audit.AdminOperation;
import com.campus.platform.module.admin.audit.AdminPermission;
import com.campus.platform.module.admin.service.AdminPermissionService;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.R;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.report.dto.ReportHandleDTO;
import com.campus.platform.module.report.entity.Report;
import com.campus.platform.module.report.service.ReportAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/report")
@RequiredArgsConstructor
public class AdminReportController {
    private final ReportAdminService reportAdminService;
    private final AdminPermissionService adminPermissions;

    @GetMapping("/list")
    public R<PageResult<Report>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long record,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(reportAdminService.list(status, record, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Report> detail(@PathVariable Long id) {
        return R.ok(reportAdminService.detail(id));
    }

    @GetMapping("/{id}/chat-evidence")
    public R<ReportAdminService.ChatEvidence> chatEvidence(@PathVariable Long id) {
        adminPermissions.require(AdminPermission.REPORT_HANDLE);
        return R.ok(reportAdminService.chatEvidence(id));
    }

    @PutMapping("/{id}/handle")
    @AdminOperation(permission = AdminPermission.REPORT_HANDLE, module = "report", action = "handle", idArgument = 0)
    public R<Void> handle(@PathVariable Long id, @Valid @RequestBody ReportHandleDTO dto) {
        reportAdminService.handle(UserContext.getUid(), id, dto);
        return R.ok();
    }
}
