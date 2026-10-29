package com.campus.platform.module.auth.controller;

import com.campus.platform.module.auth.dto.AdminLoginDTO;
import com.campus.platform.module.auth.vo.AdminLoginVO;
import com.campus.platform.module.auth.service.AuthService;
import com.campus.platform.module.auth.service.CaptchaService;

import com.campus.platform.common.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {
    private final AuthService authService;
    private final CaptchaService captchaService;

    @PostMapping("/login")
    public R<AdminLoginVO> login(@Valid @RequestBody AdminLoginDTO dto) {
        captchaService.validateAndConsume(dto.getCaptchaId(), dto.getCaptchaCode());
        return R.ok(authService.adminLogin(dto));
    }
}
