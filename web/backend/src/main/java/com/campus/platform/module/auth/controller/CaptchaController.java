package com.campus.platform.module.auth.controller;

import com.campus.platform.common.R;
import com.campus.platform.module.auth.service.CaptchaService;
import com.campus.platform.module.auth.vo.CaptchaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图形验证码接口：登录前获取验证码图片，登录时携带 captchaId + 用户输入回传校验。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    @GetMapping("/captcha")
    public R<CaptchaVO> captcha() {
        return R.ok(captchaService.generate());
    }
}
