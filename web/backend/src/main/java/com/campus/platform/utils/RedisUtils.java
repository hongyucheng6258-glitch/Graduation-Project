package com.campus.platform.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 工具：封装常用操作（限流计数、缓存读写）。
 */
@Component
@RequiredArgsConstructor
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Object getAndDelete(String key) {
        // 兼容 Redis 6.2 以下版本：Spring Data 的 getAndDelete 会发送 GETDEL，旧服务端不支持。
        // WebSocket ticket 只在握手消费，使用事务中的 GET + DEL 保持单次消费语义。
        java.util.List<Object> result = redisTemplate.execute(new org.springframework.data.redis.core.SessionCallback<>() {
            @Override
            public java.util.List<Object> execute(org.springframework.data.redis.core.RedisOperations operations) {
                operations.multi();
                operations.opsForValue().get(key);
                operations.delete(key);
                return operations.exec();
            }
        });
        return result == null || result.isEmpty() ? null : result.get(0);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 自增并设置过期时间（首次自增时设置）。
     * 用于 AI 每日限流计数：key=ai:rate:{uid}:{yyyyMMdd}
     *
     * @return 自增后的值
     */
    public Long incr(String key, long timeout, TimeUnit unit) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, timeout, unit);
        }
        return value;
    }

    public void hSet(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    public Object hGet(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    public void hDelete(String key, Object... hashKeys) {
        redisTemplate.opsForHash().delete(key, hashKeys);
    }
}
