package com.campus.platform.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生注册请求。
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "学号不能为空")
    private String studentNo;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码至少6位")
    private String password;

    @NotBlank(message = "昵称不能为空")
    private String nickname;

    @NotBlank(message = "验证码ID不能为空")
    private String captchaId;

    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
