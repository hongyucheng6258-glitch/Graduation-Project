package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.audit.AdminOperation;
import com.campus.platform.module.admin.audit.AdminPermission;

import com.campus.platform.module.admin.service.AuditService;
import com.campus.platform.module.admin.dto.AuditDTO;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.Result;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.idle.entity.IdleItem;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {
    private final AuditService auditService;

    @GetMapping("/pending")
    public Result<PageResult<?>> pendingList(
            @RequestParam(defaultValue = "idle") String type,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(defaultValue = "false") boolean riskOnly) {
        return Result.ok(auditService.pendingList(type, pageNum, pageSize, riskOnly));
    }

    @GetMapping("/idle/{id}")
    public Result<IdleItem> idleDetail(@PathVariable Long id) {
        return Result.ok(auditService.idleDetail(id));
    }

    @GetMapping("/activity/{id}")
    public Result<Activity> activityDetail(@PathVariable Long id) {
        return Result.ok(auditService.activityDetail(id));
    }

    @GetMapping("/all")
    public Result<PageResult<?>> all(
            @RequestParam String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long record,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(auditService.allList(type, keyword, record, pageNum, pageSize));
    }

    @PutMapping("/{type}/{id}/pass")
    @AdminOperation(permission = AdminPermission.CONTENT_REVIEW, module = "audit", action = "pass", idArgument = 1)
    public Result<Void> pass(@PathVariable String type, @PathVariable Long id,
                        @RequestParam(defaultValue = "false") boolean pendingOnly) {
        auditService.pass(type, id, pendingOnly);
        return Result.ok();
    }

    @PutMapping("/{type}/{id}/reject")
    @AdminOperation(permission = AdminPermission.CONTENT_REVIEW, module = "audit", action = "reject", idArgument = 1)
    public Result<Void> reject(@PathVariable String type, @PathVariable Long id, @Valid @RequestBody AuditDTO dto,
                          @RequestParam(defaultValue = "false") boolean pendingOnly) {
        auditService.reject(type, id, dto.getReason(), pendingOnly);
        return Result.ok();
    }
}
