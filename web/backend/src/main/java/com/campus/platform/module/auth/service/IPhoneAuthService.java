package com.campus.platform.module.auth.service;

import com.campus.platform.module.auth.vo.LoginVO;

/**
 * 手机号验证服务接口：绑定、验证码登录、重置密码。
 */
public interface IPhoneAuthService {

    /** 手机号绑定结果 */
    record Binding(String phone) {}

    /** 验证码发送结果（consoleCode 仅控制台开启时返回） */
    record Delivery(String consoleCode, int expiresInSeconds) {}

    /** 短信验证能力是否启用 */
    boolean available();

    /** 当前绑定的手机号 */
    Binding binding(long uid);

    /** 发送验证码 */
    Delivery send(String purpose, String phone, Long uid);

    /** 绑定手机号 */
    Binding bind(long uid, String phone, String code);

    /** 手机号验证码登录 */
    LoginVO login(String phone, String code);

    /** 重置密码 */
    void reset(String phone, String code, String password);
}
