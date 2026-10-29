package com.campus.platform.module.site.controller;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

import java.util.Map;

/**
 * 容器存活检查接口。该接口不访问数据库或 Redis，避免把依赖服务状态
 * 与 Spring Boot 进程本身的启动状态混在一起。
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    @GetMapping("/healthz")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    /** 依赖就绪检查：数据库或 Redis 不可用时返回 503，供 Compose healthcheck 使用。 */
    @GetMapping("/readyz")
    public ResponseEntity<Map<String, String>> ready() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
            statement.setQueryTimeout(2);
            statement.executeQuery();
            String pong;
            try (var redis = redisConnectionFactory.getConnection()) {
                pong = redis.ping();
            }
            if (!"PONG".equalsIgnoreCase(pong)) throw new IllegalStateException("Redis ping failed");
            return ResponseEntity.ok(Map.of("status", "READY"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "NOT_READY"));
        }
    }
}
