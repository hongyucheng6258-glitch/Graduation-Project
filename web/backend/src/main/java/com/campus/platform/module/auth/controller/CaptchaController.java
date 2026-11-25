package com.campus.platform.module.auth.controller;

import com.campus.platform.common.Result;
import com.campus.platform.module.auth.service.ICaptchaService;
import com.campus.platform.module.auth.vo.CaptchaVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图形验证码接口：登录前获取验证码图片，登录时携带 captchaId + 用户输入回传校验。
 */
@RestController
@RequestMapping("/api/auth")
public class CaptchaController {

    @Resource
    private ICaptchaService captchaService;

    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.ok(captchaService.generate());
    }
}
