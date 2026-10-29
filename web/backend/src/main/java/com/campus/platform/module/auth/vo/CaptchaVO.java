package com.campus.platform.module.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证码响应：captchaId 用于登录时回传。
 * 图形模式：mode=image + image(base64 PNG)；运算模式：mode=math + expression(如 "8 + 5 = ?")。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaVO {

    /** 验证码唯一标识（Redis key 后缀），登录时原样回传 */
    private String captchaId;

    /** 验证码模式：image=图形验证码 / math=运算验证码 */
    private String mode;

    /** 图形验证码图片，data:image/png;base64, 前缀（math 模式为 null） */
    private String image;

    /** 运算验证码算式文本，如 "8 + 5 = ?"（image 模式为 null） */
    private String expression;
}
