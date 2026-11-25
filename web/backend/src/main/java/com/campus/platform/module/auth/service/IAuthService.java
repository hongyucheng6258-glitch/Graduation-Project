package com.campus.platform.module.auth.service;

import com.campus.platform.module.auth.dto.AdminLoginDTO;
import com.campus.platform.module.auth.dto.LoginDTO;
import com.campus.platform.module.auth.dto.RegisterDTO;
import com.campus.platform.module.auth.vo.AdminLoginVO;
import com.campus.platform.module.auth.vo.LoginVO;
import com.campus.platform.module.user.entity.User;

/**
 * 认证服务接口：Web 注册/登录、管理员登录、密码工具。
 */
public interface IAuthService {

    /** Web 注册（学号+密码+昵称） */
    LoginVO register(RegisterDTO dto);

    /** Web 账号密码登录 */
    LoginVO login(LoginDTO dto);

    /** 管理员独立登录 */
    AdminLoginVO adminLogin(AdminLoginDTO dto);

    /** 密码校验（供改密复用） */
    boolean matches(String raw, String encoded);

    /** BCrypt 加密 */
    String encode(String raw);

    /** 已通过手机号验证的账号直接登录 */
    LoginVO loginVerifiedPhone(User user);
}
