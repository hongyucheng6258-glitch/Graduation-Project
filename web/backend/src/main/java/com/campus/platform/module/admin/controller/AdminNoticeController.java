package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.audit.AdminOperation;
import com.campus.platform.module.admin.audit.AdminPermission;

import com.campus.platform.module.admin.service.AdminNoticeService;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.R;
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
    private final AdminNoticeService adminNoticeService;

    @GetMapping("/list")
    public R<PageResult<Notice>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(adminNoticeService.list(status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Notice> detail(@PathVariable Long id) {
        return R.ok(adminNoticeService.detail(id));
    }

    @PostMapping
    @AdminOperation(permission = AdminPermission.NOTICE_WRITE, module = "notice", action = "create")
    public R<Notice> create(@Valid @RequestBody NoticeSaveDTO dto) {
        return R.ok(adminNoticeService.create(UserContext.getUid(), dto));
    }

    @PutMapping("/{id}")
    @AdminOperation(permission = AdminPermission.NOTICE_WRITE, module = "notice", action = "update", idArgument = 0)
    public R<Notice> update(@PathVariable Long id, @Valid @RequestBody NoticeSaveDTO dto) {
        return R.ok(adminNoticeService.update(id, dto));
    }

    @PutMapping("/{id}/publish")
    @AdminOperation(permission = AdminPermission.NOTICE_WRITE, module = "notice", action = "publish", idArgument = 0)
    public R<Void> publish(@PathVariable Long id) {
        adminNoticeService.publish(id);
        return R.ok();
    }

    @PutMapping("/{id}/offline")
    @AdminOperation(permission = AdminPermission.NOTICE_WRITE, module = "notice", action = "offline", idArgument = 0)
    public R<Void> offline(@PathVariable Long id) {
        adminNoticeService.offline(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @AdminOperation(permission = AdminPermission.NOTICE_WRITE, module = "notice", action = "delete", idArgument = 0)
    public R<Void> delete(@PathVariable Long id) {
        adminNoticeService.delete(id);
        return R.ok();
    }
}
