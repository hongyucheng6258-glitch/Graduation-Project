package com.campus.platform.module.auth.vo;

import com.campus.platform.module.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应：token + 用户信息。
 */
@Data
@AllArgsConstructor
public class LoginVO {

    private String token;

    private User userInfo;
}
