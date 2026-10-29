package com.campus.platform.module.auth.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.auth.vo.CaptchaVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 验证码服务：图形（Hutool 字符图）与运算（数字算式）两种模式随机出现，
 * Redis 存储答案（一次性使用、TTL 过期，值带模式前缀 "image:" / "math:"）。
 * 校验在 Controller 层调用，Service 层登录逻辑不感知验证码，避免影响既有单元测试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String KEY_PREFIX = "captcha:";
    private static final String MODE_IMAGE = "image";
    private static final String MODE_MATH = "math";
    /** 去除易混淆字符 0/O、1/l/I */
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int CODE_LENGTH = 4;

    private final StringRedisTemplate redisTemplate;

    @Value("${platform.captcha.expire-seconds:300}")
    private long expireSeconds;

    /** 验证码开关：false 时跳过校验（便于自动化测试/本地联调） */
    @Value("${platform.captcha.enabled:true}")
    private boolean enabled;

    /** 运算验证码出现比例（0~1），其余为图形验证码 */
    @Value("${platform.captcha.math-ratio:0.5}")
    private double mathRatio;

    /**
     * 生成验证码：随机选择图形或运算模式，答案写入 Redis（TTL 过期）。
     */
    public CaptchaVO generate() {
        if (ThreadLocalRandom.current().nextDouble() < mathRatio) {
            return generateMath();
        }
        return generateImage();
    }

    /** 图形验证码：Hutool 字符图 */
    private CaptchaVO generateImage() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(150, 50, CODE_LENGTH, 40);
        // 替换随机字符为去混淆字符集（Hutool 默认字符集含 0/O/1/l/I）
        captcha.setGenerator(new cn.hutool.captcha.generator.RandomGenerator(CHARS, CODE_LENGTH));
        captcha.createCode();
        String captchaId = IdUtil.fastSimpleUUID();
        redisTemplate.opsForValue().set(KEY_PREFIX + captchaId, MODE_IMAGE + ":" + captcha.getCode(), Duration.ofSeconds(expireSeconds));
        return new CaptchaVO(captchaId, MODE_IMAGE, "data:image/png;base64," + captcha.getImageBase64(), null);
    }

    /** 运算验证码：小学难度加减乘，答案 ≤ 100 且非负 */
    private CaptchaVO generateMath() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int a, b, result;
        String expr;
        switch (rnd.nextInt(3)) {
            case 0 -> {
                a = rnd.nextInt(5, 50);
                b = rnd.nextInt(1, 50);
                result = a + b;
                expr = a + " + " + b + " = ?";
            }
            case 1 -> {
                a = rnd.nextInt(10, 50);
                b = rnd.nextInt(1, a - 1);
                result = a - b;
                expr = a + " - " + b + " = ?";
            }
            default -> {
                a = rnd.nextInt(2, 10);
                b = rnd.nextInt(2, 10);
                result = a * b;
                expr = a + " × " + b + " = ?";
            }
        }
        String captchaId = IdUtil.fastSimpleUUID();
        redisTemplate.opsForValue().set(KEY_PREFIX + captchaId, MODE_MATH + ":" + result, Duration.ofSeconds(expireSeconds));
        return new CaptchaVO(captchaId, MODE_MATH, null, expr);
    }

    /**
     * 校验并消费验证码：无论成败立即删除（一次性，防重放）。
     * 校验失败抛出 BizException(400, "验证码错误或已过期")。
     */
    public void validateAndConsume(String captchaId, String code) {
        if (!enabled) {
            return;
        }
        if (StrUtil.isBlank(captchaId) || StrUtil.isBlank(code)) {
            throw new BizException(ResultCode.BAD_REQUEST, "验证码不能为空");
        }
        String key = KEY_PREFIX + captchaId;
        String saved = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        if (saved == null || !matches(saved, code)) {
            throw new BizException(ResultCode.BAD_REQUEST, "验证码错误或已过期");
        }
    }

    /** 按模式前缀比对答案：图形=忽略大小写字符；运算=整数精确相等 */
    private boolean matches(String saved, String code) {
        int idx = saved.indexOf(':');
        if (idx <= 0) {
            return false;
        }
        String mode = saved.substring(0, idx);
        String answer = saved.substring(idx + 1);
        String input = code.trim();
        if (MODE_IMAGE.equals(mode)) {
            return answer.equalsIgnoreCase(input);
        }
        if (MODE_MATH.equals(mode)) {
            return StrUtil.isNumeric(input) && Integer.parseInt(answer) == Integer.parseInt(input);
        }
        return false;
    }
}
