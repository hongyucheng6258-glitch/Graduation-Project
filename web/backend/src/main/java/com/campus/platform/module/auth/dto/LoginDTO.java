package com.campus.platform.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 学生登录请求。
 */
@Data
public class LoginDTO {

    @NotBlank(message = "学号不能为空")
    private String studentNo;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 图形验证码 ID（来自 /api/auth/captcha） */
    private String captchaId;

    /** 用户输入的图形验证码 */
    private String captchaCode;
}
