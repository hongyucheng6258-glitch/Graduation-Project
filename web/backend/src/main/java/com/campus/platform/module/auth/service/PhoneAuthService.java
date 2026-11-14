package com.campus.platform.module.auth.service;

import com.campus.platform.common.BizException;
import com.campus.platform.module.auth.mapper.VerifiedPhoneMapper;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PhoneAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> VERIFY = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return 0 end
            if string.sub(value,1,6) == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return 1
            end
            local remaining = tonumber(string.sub(value,7)) - 1
            local ttl = redis.call('PTTL', KEYS[1])
            if remaining <= 0 or ttl <= 0 then redis.call('DEL', KEYS[1])
            else redis.call('PSETEX', KEYS[1], ttl, string.sub(value,1,6)..remaining) end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final VerifiedPhoneMapper phones;
    private final UserMapper users;
    private final AuthService auth;
    private final com.campus.platform.module.admin.service.AdminUserService accountAdministration;

    @Value("${platform.sms.console-enabled:false}")
    private boolean consoleEnabled;

    public boolean available() { return consoleEnabled; }

    public record Binding(String phone) {}
    public record Delivery(String consoleCode, int expiresInSeconds) {}

    public Binding binding(long uid) {
        return new Binding(phones.findPhone(uid));
    }

    public Delivery send(String purpose, String phone, Long uid) {
        requireConsole();
        validatePhone(phone);
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("phone-auth:cooldown:" + phone, "1", Duration.ofMinutes(1)))) {
            throw new BizException(ResultCode.RATE_LIMITED, "请等待 60 秒后再发送验证码");
        }
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        redis.opsForValue().set(key(purpose, phone, uid), code + "5", Duration.ofMinutes(5));
        return new Delivery(code, 300);
    }

    @Transactional
    public Binding bind(long uid, String phone, String code) {
        verify("bind", phone, uid, code);
        try {
            int updated = phones.updatePhone(uid, phone);
            if (updated == 0 && !phone.equals(binding(uid).phone())) {
                phones.insertPhone(uid, phone);
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "手机号无法绑定，请核对号码或联系管理员");
        }
        return new Binding(phone);
    }

    public com.campus.platform.module.auth.vo.LoginVO login(String phone, String code) {
        verify("login", phone, null, code);
        return auth.loginVerifiedPhone(requireAccount(phone));
    }

    public void reset(String phone, String code, String password) {
        if (password == null || password.length() < 6 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码至少 6 位且不能超过 72 字节");
        }
        verify("reset", phone, null, code);
        accountAdministration.setRecoveredPassword(requireAccount(phone), password);
    }

    private User requireAccount(String phone) {
        Long uid = phones.findUserId(phone);
        User user = uid == null ? null : users.selectById(uid);
        if (user == null || !Integer.valueOf(0).equals(user.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "此号码暂不能使用该功能，请联系管理员");
        }
        return user;
    }

    private void verify(String purpose, String phone, Long uid, String code) {
        requireConsole();
        validatePhone(phone);
        if (code == null || !code.matches("[0-9]{6}")
                || !Long.valueOf(1).equals(redis.execute(VERIFY, List.of(key(purpose, phone, uid)), code))) {
            throw new BizException(ResultCode.BAD_REQUEST, "验证码错误或已失效");
        }
    }

    private void requireConsole() {
        if (!consoleEnabled) throw new BizException(ResultCode.BAD_REQUEST, "手机号验证码暂未启用，请使用密码或人工找回");
    }

    private static void validatePhone(String phone) {
        if (phone == null || !phone.matches("1[3-9][0-9]{9}")) {
            throw new BizException(ResultCode.BAD_REQUEST, "请输入 11 位有效手机号");
        }
    }

    private static String key(String purpose, String phone, Long uid) {
        return "phone-auth:code:" + purpose + ":" + (uid == null ? "" : uid + ":") + phone;
    }
}
