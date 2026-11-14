package com.campus.platform.module.auth.controller;

import com.campus.platform.module.auth.dto.AdminLoginDTO;
import com.campus.platform.module.auth.vo.AdminLoginVO;
import com.campus.platform.module.auth.service.AuthService;
import com.campus.platform.module.auth.service.CaptchaService;

import com.campus.platform.common.R;
import com.campus.platform.module.admin.service.AdminPermissionService;

import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {
    private final AuthService authService;
    private final CaptchaService captchaService;
    private final AdminPermissionService permissions;

    @PostMapping("/login")
    public R<AdminLoginVO> login(@Valid @RequestBody AdminLoginDTO dto) {
        captchaService.validateAndConsume(dto.getCaptchaId(), dto.getCaptchaCode());
        return R.ok(authService.adminLogin(dto));
    }

    @GetMapping("/me")
    public R<AdminSession> me() {
        var admin = permissions.currentAdmin();
        return R.ok(new AdminSession(String.valueOf(admin.getId()), admin.getUsername(), admin.getNickname(),
                admin.getAvatar(), admin.getRole(), permissions.permissionsFor(admin)));
    }

    public record AdminSession(String id, String username, String nickname, String avatar,
                               String role, List<String> permissions) {}
}
