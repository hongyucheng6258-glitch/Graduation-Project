package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.service.AdminPermissionService;
import com.campus.platform.module.admin.service.AdminUserService;
import com.campus.platform.module.admin.dto.AdminSaveDTO;
import com.campus.platform.module.admin.entity.Admin;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/system/admin")
@RequiredArgsConstructor
public class AdminSystemController {
    private final AdminUserService adminUserService;
    private final AdminPermissionService adminPermissionService;

    @GetMapping
    public R<PageResult<Admin>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        adminPermissionService.requireSuper();
        return R.ok(adminUserService.listAdmins(pageNum, pageSize));
    }

    @PostMapping
    public R<Admin> create(@Valid @RequestBody AdminSaveDTO dto) {
        adminPermissionService.requireSuper();
        return R.ok(adminUserService.createAdmin(dto));
    }

    @PutMapping("/{id}")
    public R<Admin> update(@PathVariable Long id, @Valid @RequestBody AdminSaveDTO dto) {
        adminPermissionService.requireSuper();
        return R.ok(adminUserService.updateAdmin(id, dto));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        adminPermissionService.requireSuper();
        adminUserService.deleteAdmin(id, com.campus.platform.common.UserContext.getUid());
        return R.ok();
    }
}
