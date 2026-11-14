package com.campus.platform.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.data.redis.RedisConnectionFailureException;

@EnabledIfEnvironmentVariable(named = "CACHE_TEST_REDIS_PORT", matches = "[0-9]+")
class RedisCacheIntegrationTest {
    private static final TypeReference<String> TEXT = new TypeReference<>() {};
    private static final Duration TTL = Duration.ofSeconds(30);
    private final String key = "integration-" + UUID.randomUUID();
    private LettuceConnectionFactory connection;
    private StringRedisTemplate redis;
    private RedissonClient firstClient;
    private RedissonClient secondClient;
    private RedisCache first;
    private RedisCache second;

    @BeforeEach
    void connect() {
        var address = new RedisStandaloneConfiguration("127.0.0.1", Integer.parseInt(System.getenv("CACHE_TEST_REDIS_PORT")));
        connection = new LettuceConnectionFactory(address);
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + address.getHostName() + ":" + address.getPort())
                .setConnectionMinimumIdleSize(1).setSubscriptionConnectionMinimumIdleSize(1);
        firstClient = Redisson.create(config);
        secondClient = Redisson.create(config);
        first = new RedisCache(redis, new ObjectMapper(), firstClient);
        second = new RedisCache(redis, new ObjectMapper(), secondClient);
    }

    @AfterEach
    void disconnect() {
        first.invalidate(key);
        firstClient.shutdown();
        secondClient.shutdown();
        connection.destroy();
    }

    @Test
    void sharesNegativeCacheAndUsesShortExpiration() {
        var calls = new AtomicInteger();
        assertThat(first.get(key, TEXT, TTL, () -> { calls.incrementAndGet(); return null; })).isNull();
        assertThat(second.get(key, TEXT, TTL, () -> { calls.incrementAndGet(); return "unexpected"; })).isNull();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(redis.getExpire(dataKey(), TimeUnit.MILLISECONDS)).isBetween(1L, 6000L);
        first.invalidate(key);
        assertThat(second.get(key, TEXT, TTL, () -> "created")).isEqualTo("created");
        assertThat(redis.getExpire(dataKey(), TimeUnit.MILLISECONDS)).isBetween(29000L, 36000L);
    }

    @Test
    void onlyOneInstanceLoadsOnConcurrentMiss() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var original = executor.submit(() -> first.get(key, TEXT, TTL, () -> {
                calls.incrementAndGet();
                started.countDown();
                await(release);
                return "value";
            }));
            await(started);
            var concurrent = executor.submit(() -> second.get(key, TEXT, TTL, () -> {
                calls.incrementAndGet();
                return "unexpected";
            }));
            release.countDown();
            assertThat(original.get(3, TimeUnit.SECONDS)).isEqualTo("value");
            assertThat(concurrent.get(3, TimeUnit.SECONDS)).isEqualTo("value");
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void invalidationFencesLoaderThatStartedBeforeCommit() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var old = executor.submit(() -> first.get(key, TEXT, TTL, () -> {
                started.countDown();
                await(release);
                return "old";
            }));
            await(started);
            second.invalidate(key);
            release.countDown();
            assertThat(old.get(3, TimeUnit.SECONDS)).isEqualTo("old");
            assertThat(redis.hasKey(dataKey())).isFalse();
            assertThat(second.get(key, TEXT, TTL, () -> "new")).isEqualTo("new");
            assertThat(first.get(key, TEXT, TTL, () -> "unexpected")).isEqualTo("new");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void spreadsExpirationAcrossKeys() {
        var expirationTimes = new HashSet<Long>();
        for (int index = 0; index < 12; index++) {
            String distinct = key + "-" + index;
            first.get(distinct, TEXT, TTL, () -> "value");
            try {
                expirationTimes.add(redis.getExpire("campus:cache:v1:{" + distinct + "}:data",
                        TimeUnit.MILLISECONDS) / 500);
            } finally {
                first.invalidate(distinct);
            }
        }
        assertThat(expirationTimes).hasSizeGreaterThan(1);
    }

    @Test
    void fallsBackToDatabaseWhenRedisIsUnavailable() {
        var unavailable = mock(StringRedisTemplate.class);
        when(unavailable.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis offline"));
        var cache = new RedisCache(unavailable, new ObjectMapper(), firstClient);
        assertThat(cache.get(key, TEXT, TTL, () -> "database")).isEqualTo("database");
    }

    private String dataKey() {
        return "campus:cache:v1:{" + key + "}:data";
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
