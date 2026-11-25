package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.audit.AdminOperation;
import com.campus.platform.module.admin.audit.AdminPermission;

import com.campus.platform.module.admin.service.IAdminNoticeService;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.notice.dto.NoticeSaveDTO;
import com.campus.platform.module.notice.entity.Notice;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/notice")
@RequiredArgsConstructor
public class AdminNoticeController {
    private final IAdminNoticeService adminNoticeService;

    @GetMapping("/list")
    public Result<PageResult<Notice>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(adminNoticeService.list(status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<Notice> detail(@PathVariable Long id) {
        return Result.ok(adminNoticeService.detail(id));
    }

    @PostMapping
    @AdminOperation(permission = AdminPermission.NOTICE_WRITE, module = "notice", action = "create")
    public Result<Notice> create(@Valid @RequestBody NoticeSaveDTO dto) {
        return Result.ok(adminNoticeService.create(UserContext.getUid(), dto));
    }

    @PutMapping("/{id}")
    @AdminOperation(permission = AdminPermission.NOTICE_WRITE, module = "notice", action = "update", idArgument = 0)
    public Result<Notice> update(@PathVariable Long id, @Valid @RequestBody NoticeSaveDTO dto) {
        return Result.ok(adminNoticeService.update(id, dto));
    }

    @PutMapping("/{id}/publish")
    @AdminOperation(permission = AdminPermission.NOTICE_WRITE, module = "notice", action = "publish", idArgument = 0)
    public Result<Void> publish(@PathVariable Long id) {
        adminNoticeService.publish(id);
        return Result.ok();
    }

    @PutMapping("/{id}/offline")
    @AdminOperation(permission = AdminPermission.NOTICE_WRITE, module = "notice", action = "offline", idArgument = 0)
    public Result<Void> offline(@PathVariable Long id) {
        adminNoticeService.offline(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @AdminOperation(permission = AdminPermission.NOTICE_WRITE, module = "notice", action = "delete", idArgument = 0)
    public Result<Void> delete(@PathVariable Long id) {
        adminNoticeService.delete(id);
        return Result.ok();
    }
}
