package com.campus.platform.module.auth.controller;

import com.campus.platform.module.auth.service.AuthService;
import com.campus.platform.module.auth.service.CaptchaService;
import com.campus.platform.module.auth.dto.RegisterDTO;
import com.campus.platform.module.auth.dto.LoginDTO;
import com.campus.platform.module.auth.vo.LoginVO;

import com.campus.platform.common.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    @PostMapping("/register")
    public R<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        captchaService.validateAndConsume(dto.getCaptchaId(), dto.getCaptchaCode());
        return R.ok(authService.register(dto));
    }

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        captchaService.validateAndConsume(dto.getCaptchaId(), dto.getCaptchaCode());
        return R.ok(authService.login(dto));
    }
}
