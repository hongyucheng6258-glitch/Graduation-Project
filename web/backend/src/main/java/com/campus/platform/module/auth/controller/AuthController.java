package com.campus.platform.module.auth.controller;

import com.campus.platform.module.auth.service.IAuthService;
import com.campus.platform.module.auth.service.ICaptchaService;
import com.campus.platform.module.auth.dto.RegisterDTO;
import com.campus.platform.module.auth.dto.LoginDTO;
import com.campus.platform.module.auth.vo.LoginVO;

import com.campus.platform.common.Result;
import jakarta.validation.Valid;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private IAuthService authService;
    @Resource
    private ICaptchaService captchaService;

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        captchaService.validateAndConsume(dto.getCaptchaId(), dto.getCaptchaCode());
        return Result.ok(authService.register(dto));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        captchaService.validateAndConsume(dto.getCaptchaId(), dto.getCaptchaCode());
        return Result.ok(authService.login(dto));
    }
}
