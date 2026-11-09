package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.AiConfigHolder;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import com.campus.platform.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class AgentQuota {
    private final AiConfigHolder config;
    private final RedisUtils redis;
    private final SensitiveWordService sensitiveWords;

    public void check(Long userId, String question) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (sensitiveWords.contains(question)) {
            throw new BizException(ResultCode.SENSITIVE_WORD);
        }
        Long used = redis.incr(Constants.REDIS_AI_RATE_LIMIT + userId + ":"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), 1, TimeUnit.DAYS);
        if (used == null || used > config.getRateLimitPerDay()) {
            throw new BizException(ResultCode.RATE_LIMITED, "今日 AI 调用次数已达上限");
        }
    }
}
