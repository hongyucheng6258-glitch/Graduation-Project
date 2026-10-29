package com.campus.platform.module.chat.service;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.config.SystemConfigHolder;
import com.campus.platform.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 私信发送限流：每秒/每分钟上限从系统配置读取，管理端可在线调整即时生效。
 */
@Component
@RequiredArgsConstructor
public class ChatRateLimiter {
    private final RedisUtils redisUtils;
    private final SystemConfigHolder systemConfigHolder;

    public void checkSend(Long userId) {
        int perSec = systemConfigHolder.getChatRatePerSec();
        int perMin = systemConfigHolder.getChatRatePerMin();
        check("chat:rate:sec:" + userId, perSec, 1, TimeUnit.SECONDS);
        check("chat:rate:min:" + userId, perMin, 1, TimeUnit.MINUTES);
    }

    private void check(String key, long limit, long timeout, TimeUnit unit) {
        Long value = redisUtils.incr(key, timeout, unit);
        if (value == null) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "发送限流服务暂不可用");
        }
        if (value > limit) {
            throw new BizException(ResultCode.FORBIDDEN, "发送过于频繁，请稍后再试");
        }
    }
}
