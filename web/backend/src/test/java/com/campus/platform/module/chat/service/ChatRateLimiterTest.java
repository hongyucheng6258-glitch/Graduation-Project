package com.campus.platform.module.chat.service;


import com.campus.platform.common.BizException;
import com.campus.platform.config.SystemConfigHolder;
import com.campus.platform.utils.RedisUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ChatRateLimiterTest {

    private SystemConfigHolder mockConfig() {
        SystemConfigHolder config = mock(SystemConfigHolder.class);
        when(config.getChatRatePerSec()).thenReturn(5);
        when(config.getChatRatePerMin()).thenReturn(100);
        return config;
    }

    @Test
    void rejectsWhenPerSecondRedisCounterExceedsLimit() {
        RedisUtils redis = mock(RedisUtils.class);
        when(redis.incr("chat:rate:sec:7", 1, TimeUnit.SECONDS)).thenReturn(6L);
        ChatRateLimiter limiter = new ChatRateLimiter(redis, mockConfig());

        assertThrows(BizException.class, () -> limiter.checkSend(7L));
        verify(redis, never()).incr("chat:rate:min:7", 1, TimeUnit.MINUTES);
    }

    @Test
    void acceptsWhenBothDistributedCountersAreWithinLimit() {
        RedisUtils redis = mock(RedisUtils.class);
        when(redis.incr("chat:rate:sec:7", 1, TimeUnit.SECONDS)).thenReturn(5L);
        when(redis.incr("chat:rate:min:7", 1, TimeUnit.MINUTES)).thenReturn(100L);
        ChatRateLimiter limiter = new ChatRateLimiter(redis, mockConfig());

        assertDoesNotThrow(() -> limiter.checkSend(7L));
    }

    @Test
    void failsClosedWhenRedisCounterReturnsNull() {
        RedisUtils redis = mock(RedisUtils.class);
        when(redis.incr("chat:rate:sec:7", 1, TimeUnit.SECONDS)).thenReturn(null);
        ChatRateLimiter limiter = new ChatRateLimiter(redis, mockConfig());

        assertThrows(BizException.class, () -> limiter.checkSend(7L));
    }
}
