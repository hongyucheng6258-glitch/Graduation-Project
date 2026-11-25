package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.audit.AdminOperation;
import com.campus.platform.module.admin.audit.AdminPermission;

import com.campus.platform.module.admin.service.IAdminUserService;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.Result;
import com.campus.platform.module.user.dto.UserStatusDTO;
import com.campus.platform.module.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {
    private final IAdminUserService adminUserService;

    @GetMapping("/list")
    public Result<PageResult<User>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long record,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(adminUserService.listUsers(keyword, status, record, pageNum, pageSize));
    }

    @PutMapping("/{id}/status")
    @AdminOperation(permission = AdminPermission.USER_STATUS, module = "user", action = "update-status", idArgument = 0)
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusDTO dto) {
        adminUserService.updateStatus(id, dto.getStatus());
        return Result.ok();
    }

    @PutMapping("/{id}/reset-password")
    @AdminOperation(permission = AdminPermission.USER_PASSWORD_RESET, module = "user", action = "reset-password", idArgument = 0)
    public Result<java.util.Map<String, String>> resetPassword(@PathVariable Long id) {
        return Result.ok(java.util.Map.of("temporaryPassword", adminUserService.resetPassword(id)));
    }
}
