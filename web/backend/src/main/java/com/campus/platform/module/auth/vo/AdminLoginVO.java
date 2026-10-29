package com.campus.platform.module.auth.vo;

import com.campus.platform.module.admin.entity.Admin;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 管理员登录响应。
 */
@Data
@AllArgsConstructor
public class AdminLoginVO {

    private String token;

    private Admin adminInfo;
}
