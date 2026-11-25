package com.campus.platform.module.auth.controller;

import com.campus.platform.module.auth.dto.AdminLoginDTO;
import com.campus.platform.module.auth.vo.AdminLoginVO;
import com.campus.platform.module.auth.service.IAuthService;
import com.campus.platform.module.auth.service.ICaptchaService;

import com.campus.platform.common.Result;
import com.campus.platform.module.admin.service.AdminPermissionService;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
    @Resource
    private IAuthService authService;
    @Resource
    private ICaptchaService captchaService;
    @Resource
    private AdminPermissionService permissions;

    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginDTO dto) {
        captchaService.validateAndConsume(dto.getCaptchaId(), dto.getCaptchaCode());
        return Result.ok(authService.adminLogin(dto));
    }

    @GetMapping("/me")
    public Result<AdminSession> me() {
        var admin = permissions.currentAdmin();
        return Result.ok(new AdminSession(String.valueOf(admin.getId()), admin.getUsername(), admin.getNickname(),
                admin.getAvatar(), admin.getRole(), permissions.permissionsFor(admin)));
    }

    public record AdminSession(String id, String username, String nickname, String avatar,
                               String role, List<String> permissions) {}
}
