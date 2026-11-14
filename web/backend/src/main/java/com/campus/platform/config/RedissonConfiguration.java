package com.campus.platform.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class RedissonConfiguration {
    @Bean(destroyMethod = "shutdown")
    RedissonClient redissonClient(RedisProperties properties) {
        Config config = new Config();
        config.setLazyInitialization(true);
        var server = config.useSingleServer()
                .setAddress((properties.getSsl().isEnabled() ? "rediss://" : "redis://")
                        + properties.getHost() + ":" + properties.getPort())
                .setDatabase(properties.getDatabase())
                .setConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setConnectTimeout(1000)
                .setTimeout(1000)
                .setRetryAttempts(0);
        if (StringUtils.hasText(properties.getUsername())) server.setUsername(properties.getUsername());
        if (StringUtils.hasText(properties.getPassword())) server.setPassword(properties.getPassword());
        return Redisson.create(config);
    }
}
