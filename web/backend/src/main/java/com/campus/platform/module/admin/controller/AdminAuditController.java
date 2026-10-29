package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.service.AuditService;
import com.campus.platform.module.admin.dto.AuditDTO;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {
    private final AuditService auditService;

    @GetMapping("/pending")
    public R<PageResult<?>> pendingList(
            @RequestParam(defaultValue = "idle") String type,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(auditService.pendingList(type, pageNum, pageSize));
    }

    @GetMapping("/all")
    public R<PageResult<?>> all(
            @RequestParam String type,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(auditService.allList(type, pageNum, pageSize));
    }

    @PutMapping("/{type}/{id}/pass")
    public R<Void> pass(@PathVariable String type, @PathVariable Long id) {
        auditService.pass(type, id);
        return R.ok();
    }

    @PutMapping("/{type}/{id}/reject")
    public R<Void> reject(@PathVariable String type, @PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        auditService.reject(type, id, dto.getReason());
        return R.ok();
    }
}
