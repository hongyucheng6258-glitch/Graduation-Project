package com.campus.platform.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员登录请求。
 */
@Data
public class AdminLoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 图形验证码 ID（来自 /api/auth/captcha） */
    private String captchaId;

    /** 用户输入的图形验证码 */
    private String captchaCode;
}
