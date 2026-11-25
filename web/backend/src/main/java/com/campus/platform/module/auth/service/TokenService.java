package com.campus.platform.module.auth.service;

import cn.dev33.satoken.stp.SaLoginModel;
import com.campus.platform.common.StpKit;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 登录令牌服务：统一封装学生 / 管理员的 Sa-Token 登录、解析与踢人。
 *
 * <p>业务代码不直接调用 Sa-Token 静态方法，便于单元测试和后续调整登录策略。
 */
@Service
public class TokenService {

    /** 默认有效期：7 天，与改造前的 JWT 保持一致 */
    private static final long DEFAULT_TIMEOUT_SECONDS = 7L * 24 * 3600;

    /** 学生登录，返回本次签发的 token */
    public String studentLogin(Long uid) {
        return studentLogin(uid, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 学生登录，token 有效期不超过临时密码的到期时间。
     *
     * @param expiresNoLaterThan 临时密码到期时间；为 null 时使用默认 7 天
     */
    public String studentLogin(Long uid, Instant expiresNoLaterThan) {
        long timeout = DEFAULT_TIMEOUT_SECONDS;
        if (expiresNoLaterThan != null) {
            long remaining = Duration.between(Instant.now(), expiresNoLaterThan).toSeconds();
            timeout = Math.max(1L, Math.min(timeout, remaining));
        }
        return studentLogin(uid, timeout);
    }

    private String studentLogin(Long uid, long timeoutSeconds) {
        StpKit.STUDENT.login(uid, new SaLoginModel().setTimeout(timeoutSeconds));
        return StpKit.STUDENT.getTokenValue();
    }

    /** 管理员登录，返回本次签发的 token */
    public String adminLogin(Long adminId) {
        StpKit.ADMIN.login(adminId, new SaLoginModel().setTimeout(DEFAULT_TIMEOUT_SECONDS));
        return StpKit.ADMIN.getTokenValue();
    }

    /** 根据学生 token 取用户 id；token 无效/过期返回 null */
    public Long studentId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return toLong(StpKit.STUDENT.getLoginIdByToken(token));
    }

    /** 根据管理员 token 取管理员 id；token 无效/过期返回 null */
    public Long adminId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return toLong(StpKit.ADMIN.getLoginIdByToken(token));
    }

    /** 踢掉某个学生的全部登录态（禁用、改密、重置密码后调用） */
    public void kickoutStudent(Long uid) {
        if (uid != null) {
            StpKit.STUDENT.kickout(uid);
        }
    }

    /** 踢掉某个管理员的全部登录态（删除、禁用、降权后调用） */
    public void kickoutAdmin(Long adminId) {
        if (adminId != null) {
            StpKit.ADMIN.kickout(adminId);
        }
    }

    private Long toLong(Object loginId) {
        if (loginId == null) {
            return null;
        }
        try {
            return Long.valueOf(loginId.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
