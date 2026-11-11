package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.service.AuditService;
import com.campus.platform.module.admin.dto.AuditDTO;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.Constants;
import com.campus.platform.common.R;
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
    public R<PageResult<?>> pendingList(
            @RequestParam(defaultValue = "idle") String type,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(defaultValue = "false") boolean riskOnly) {
        return R.ok(auditService.pendingList(type, pageNum, pageSize, riskOnly));
    }

    @GetMapping("/idle/{id}")
    public R<IdleItem> idleDetail(@PathVariable Long id) {
        return R.ok(auditService.idleDetail(id));
    }

    @GetMapping("/activity/{id}")
    public R<Activity> activityDetail(@PathVariable Long id) {
        return R.ok(auditService.activityDetail(id));
    }

    @GetMapping("/all")
    public R<PageResult<?>> all(
            @RequestParam String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long record,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(auditService.allList(type, keyword, record, pageNum, pageSize));
    }

    @PutMapping("/{type}/{id}/pass")
    public R<Void> pass(@PathVariable String type, @PathVariable Long id,
                        @RequestParam(defaultValue = "false") boolean pendingOnly) {
        if (pendingOnly && Constants.BIZ_IDLE.equals(type)) auditService.reviewPendingIdle(id, true, null);
        else if (pendingOnly && Constants.BIZ_ACTIVITY.equals(type)) auditService.reviewPendingActivity(id, true, null);
        else auditService.pass(type, id);
        return R.ok();
    }

    @PutMapping("/{type}/{id}/reject")
    public R<Void> reject(@PathVariable String type, @PathVariable Long id, @Valid @RequestBody AuditDTO dto,
                          @RequestParam(defaultValue = "false") boolean pendingOnly) {
        if (pendingOnly && Constants.BIZ_IDLE.equals(type)) auditService.reviewPendingIdle(id, false, dto.getReason());
        else if (pendingOnly && Constants.BIZ_ACTIVITY.equals(type)) auditService.reviewPendingActivity(id, false, dto.getReason());
        else auditService.reject(type, id, dto.getReason());
        return R.ok();
    }
}
