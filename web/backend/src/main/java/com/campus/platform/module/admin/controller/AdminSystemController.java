package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.service.AdminPermissionService;
import com.campus.platform.module.admin.service.IAdminUserService;
import com.campus.platform.module.admin.dto.AdminSaveDTO;
import com.campus.platform.module.admin.entity.Admin;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/system/admin")
@RequiredArgsConstructor
public class AdminSystemController {
    private final IAdminUserService adminUserService;
    private final AdminPermissionService adminPermissionService;

    @GetMapping
    public Result<PageResult<Admin>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        adminPermissionService.requireSuper();
        return Result.ok(adminUserService.listAdmins(pageNum, pageSize));
    }

    @PostMapping
    public Result<Admin> create(@Valid @RequestBody AdminSaveDTO dto) {
        adminPermissionService.requireSuper();
        return Result.ok(adminUserService.createAdmin(dto));
    }

    @PutMapping("/{id}")
    public Result<Admin> update(@PathVariable Long id, @Valid @RequestBody AdminSaveDTO dto) {
        adminPermissionService.requireSuper();
        return Result.ok(adminUserService.updateAdmin(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminPermissionService.requireSuper();
        adminUserService.deleteAdmin(id, com.campus.platform.common.UserContext.getUid());
        return Result.ok();
    }
}
