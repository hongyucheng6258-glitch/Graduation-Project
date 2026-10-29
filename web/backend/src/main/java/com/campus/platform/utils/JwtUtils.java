package com.campus.platform.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具（共享约定 #3）：
 * claims = {uid, role: "student"|"admin", exp, iat}，有效期默认 7 天。
 * R5：部署必须显式提供 JWT_SECRET；启动时校验，缺省/等于仓库公开默认值直接拒绝启动。
 */
@Component
public class JwtUtils {

    /** 仓库历史公开默认值（仅本地开发可用，生产拒绝） */
    private static final String INSECURE_DEFAULT = "campus-platform-jwt-secret-key-2024-graduation-project-must-be-long-enough";

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.expire-days:7}")
    private long expireDays;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** R5：启动即拒绝缺密钥/使用公开默认值的部署 */
    @jakarta.annotation.PostConstruct
    void validateSecret() {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("JWT_SECRET 未配置或过短（>=32 字符），拒绝启动。请在 .env 设置 JWT_SECRET。");
        }
        if (INSECURE_DEFAULT.equals(secret)) {
            throw new IllegalStateException("JWT_SECRET 仍为仓库公开默认值，拒绝启动。请在 .env 设置独立随机密钥。");
        }
    }

    /** 生成 token */
    public String generate(Long uid, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireDays * 24 * 3600 * 1000);
        return Jwts.builder()
                .claim("uid", uid)
                .claim("role", role)
                // 标准 iat 在部分 JWT 库中按秒序列化；保留毫秒自定义 claim，
                // 让管理员撤销时间在同一秒重新登录时也能区分新旧令牌。
                .claim("iat_ms", now.getTime())
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** 解析全部 claims（过期/篡改会抛异常，由拦截器捕获转401） */
    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Long getUid(String token) {
        return parse(token).get("uid", Long.class);
    }

    public String getRole(String token) {
        return parse(token).get("role", String.class);
    }

    /** 签发时间（epoch millis），用于管理员「撤销时间 vs 签发时间」比对 */
    public Long getIssuedAtMillis(String token) {
        Claims claims = parse(token);
        Number precise = claims.get("iat_ms", Number.class);
        if (precise != null) return precise.longValue();
        Date iat = claims.getIssuedAt();
        return iat == null ? null : iat.getTime();
    }
}
