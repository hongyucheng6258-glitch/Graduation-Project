package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.service.AdminUserService;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.R;
import com.campus.platform.module.user.dto.UserStatusDTO;
import com.campus.platform.module.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService adminUserService;

    @GetMapping("/list")
    public R<PageResult<User>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(adminUserService.listUsers(keyword, status, pageNum, pageSize));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusDTO dto) {
        adminUserService.updateStatus(id, dto.getStatus());
        return R.ok();
    }

    @PutMapping("/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable Long id) {
        adminUserService.resetPassword(id);
        return R.ok();
    }
}
