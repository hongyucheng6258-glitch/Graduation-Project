package com.campus.platform.utils;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Read-through cache for rebuildable database data. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisCache {
    private static final String NULL = "null";
    private static final Duration FILL_TOKEN_TTL = Duration.ofSeconds(15);
    private static final long WAIT_MS = 1500;
    private static final DefaultRedisScript<Long> WRITE = script("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then return 0 end
            redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2])
            redis.call('DEL', KEYS[2])
            return 1
            """);
    private static final DefaultRedisScript<Long> UNLOCK = script("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final RedissonClient redisson;
    private final Semaphore databaseSlots = new Semaphore(8);
    private final ConcurrentHashMap<String, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> pendingInvalidations = new ConcurrentHashMap<>();
    private volatile long retryRedisAfter = System.nanoTime();

    public <T> T get(String key, TypeReference<T> type, Duration ttl, Supplier<T> loader) {
        if (ttl.compareTo(Duration.ofSeconds(1)) < 0) throw new IllegalArgumentException("Cache TTL must be at least 1s");
        String raw = coalesce(key, () -> readThrough(key, ttl, () -> serialize(load(loader))));
        try {
            return json.readValue(raw, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot decode cached value", e);
        }
    }

    /** Call after committing the underlying database write. Also fences an in-flight fill. */
    public void invalidate(String key) {
        pendingInvalidations.put(key, new Object());
        flushInvalidation(key);
        inFlight.remove(key);
    }

    private void flushInvalidation(String key) {
        Object marker = pendingInvalidations.get(key);
        if (marker == null) return;
        try {
            redis.delete(List.of(dataKey(key), fillKey(key)));
            pendingInvalidations.remove(key, marker);
        } catch (DataAccessException e) {
            redisUnavailable(e);
        }
    }

    private String readThrough(String key, Duration ttl, Supplier<String> loader) {
        if (System.nanoTime() < retryRedisAfter) return loader.get();
        if (pendingInvalidations.containsKey(key)) {
            flushInvalidation(key);
            if (pendingInvalidations.containsKey(key)) return loader.get();
        }
        String data = dataKey(key);
        String fill = fillKey(key);
        String token = UUID.randomUUID().toString();
        var lock = redisson.getLock("campus:cache:rebuild:{" + key + "}");
        try {
            String hit = redis.opsForValue().get(data);
            if (hit != null) return hit;
            if (!lock.tryLock(WAIT_MS, TimeUnit.MILLISECONDS)) {
                hit = redis.opsForValue().get(data);
                if (hit != null) return hit;
                throw busy();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw busy();
        } catch (DataAccessException | RedisException e) {
            redisUnavailable(e);
            return loader.get();
        }
        try {
            try {
                // Set the write fence before checking the cache again. Invalidation removes it.
                redis.opsForValue().set(fill, token, FILL_TOKEN_TTL);
                String hit = redis.opsForValue().get(data);
                if (hit != null) return hit;
            } catch (DataAccessException e) {
                redisUnavailable(e);
                return loader.get();
            }
            String value = loader.get();
            long base = NULL.equals(value) ? Math.min(5000, ttl.toMillis()) : ttl.toMillis();
            long expiration = base + ThreadLocalRandom.current().nextLong(base / 5 + 1);
            try {
                redis.execute(WRITE, List.of(data, fill), token, value, Long.toString(expiration));
            } catch (DataAccessException e) {
                redisUnavailable(e);
            }
            return value;
        } finally {
            try {
                redis.execute(UNLOCK, List.of(fill), token);
            } catch (DataAccessException e) {
                redisUnavailable(e);
            } finally {
                try {
                    lock.unlock();
                } catch (IllegalMonitorStateException e) {
                    log.debug("Cache rebuild lock expired before release");
                } catch (RedisException e) {
                    redisUnavailable(e);
                }
            }
        }
    }

    private String coalesce(String key, Supplier<String> work) {
        var mine = new CompletableFuture<String>();
        var existing = inFlight.putIfAbsent(key, mine);
        if (existing == null) {
            try {
                String result = work.get();
                mine.complete(result);
                return result;
            } catch (RuntimeException | Error error) {
                mine.completeExceptionally(error);
                throw error;
            } finally {
                inFlight.remove(key, mine);
            }
        }
        try {
            return existing.get(WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw busy();
        } catch (TimeoutException e) {
            throw busy();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException error) throw error;
            if (e.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(e.getCause());
        }
    }

    private <T> T load(Supplier<T> loader) {
        if (!databaseSlots.tryAcquire()) throw busy();
        try {
            return loader.get();
        } finally {
            databaseSlots.release();
        }
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot encode cached value", e);
        }
    }

    private void redisUnavailable(RuntimeException error) {
        long now = System.nanoTime();
        if (now >= retryRedisAfter) log.warn("Redis cache unavailable: {}", error.getClass().getSimpleName());
        retryRedisAfter = now + TimeUnit.SECONDS.toNanos(2);
    }

    private static BizException busy() {
        return new BizException(ResultCode.RATE_LIMITED, "数据正在加载，请稍后重试");
    }

    private static String dataKey(String key) {
        return "campus:cache:v1:{" + key + "}:data";
    }

    private static String fillKey(String key) {
        return "campus:cache:v1:{" + key + "}:fill";
    }

    private static DefaultRedisScript<Long> script(String text) {
        return new DefaultRedisScript<>(text, Long.class);
    }
}
