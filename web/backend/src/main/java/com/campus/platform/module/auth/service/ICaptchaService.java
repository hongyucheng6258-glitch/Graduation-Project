package com.campus.platform.module.auth.service;

import com.campus.platform.module.auth.vo.CaptchaVO;

/**
 * 验证码服务接口：图形 / 运算验证码的生成与一次性校验。
 */
public interface ICaptchaService {

    /** 生成验证码（答案写入 Redis，TTL 过期） */
    CaptchaVO generate();

    /** 校验并消费验证码（一次性使用） */
    void validateAndConsume(String captchaId, String code);
}
