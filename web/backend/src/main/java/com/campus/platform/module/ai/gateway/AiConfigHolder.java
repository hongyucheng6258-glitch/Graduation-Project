package com.campus.platform.module.ai.gateway;

import cn.hutool.core.util.StrUtil;
import com.campus.platform.module.ai.entity.AiConfig;
import com.campus.platform.module.ai.mapper.AiConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.utils.RedisCache;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


/**
 * AI 配置持有者：DB 值优先于环境变量/YAML 默认值，修改后调 refresh() 免重启。
 * <p>
 * 优先级：① ai_config 表非空值 → ② 环境变量 → ③ application.yml 默认值
 */
@Component
@RequiredArgsConstructor
public class AiConfigHolder {

    private static final String CACHE_KEY = "ai-config";
    private static final String API_KEY = "api_key";
    private static final TypeReference<Map<String, String>> CONFIG_TYPE = new TypeReference<>() {};
    private final AiConfigMapper aiConfigMapper;
    private final RedisCache cache;

    @Value("${ai.base-url:https://api.deepseek.com}")
    private String defaultBaseUrl;
    @Value("${ai.api-key:sk-xxx}")
    private String defaultApiKey;
    @Value("${ai.model-name:deepseek-chat}")
    private String defaultModel;
    @Value("${ai.temperature:0.7}")
    private double defaultTemperature;
    @Value("${ai.max-tokens:2048}")
    private int defaultMaxTokens;
    @Value("${ai.timeout-ms:60000}")
    private int defaultTimeoutMs;
    @Value("${ai.retry-times:2}")
    private int defaultRetryTimes;
    @Value("${ai.rate-limit-per-day:50}")
    private int defaultRateLimit;

    public void refresh() {
        cache.invalidate(CACHE_KEY);
    }

    private Map<String, String> load() {
        Map<String, String> values = new HashMap<>();
        var configs = aiConfigMapper.selectList(new LambdaQueryWrapper<AiConfig>()
                .ne(AiConfig::getConfigKey, API_KEY));
        for (AiConfig config : configs) {
            if (StrUtil.isNotBlank(config.getConfigValue())) {
                values.put(config.getConfigKey(), config.getConfigValue());
            }
        }
        return values;
    }

    private String databaseApiKey() {
        var config = aiConfigMapper.selectOne(new LambdaQueryWrapper<AiConfig>()
                .eq(AiConfig::getConfigKey, API_KEY));
        return config == null ? null : config.getConfigValue();
    }

    /**
     * 获取配置值：DB 非空值 → 环境变量 → YAML 默认值。
     */
    public String get(String key) {
        String dbVal = API_KEY.equals(key) ? databaseApiKey()
                : cache.get(CACHE_KEY, CONFIG_TYPE, Duration.ofMinutes(1), this::load).get(key);
        if (StrUtil.isNotBlank(dbVal)) {
            return dbVal;
        }
        return switch (key) {
            case "base_url" -> defaultBaseUrl;
            case "api_key" -> {
                String apiKey = System.getenv("AI_API_KEY");
                if (StrUtil.isBlank(apiKey)) apiKey = System.getenv("DEEPSEEK_API_KEY");
                yield StrUtil.isBlank(apiKey) ? defaultApiKey : apiKey;
            }
            case "model_name" -> System.getenv("AI_MODEL") != null ? System.getenv("AI_MODEL") : defaultModel;
            case "temperature" -> String.valueOf(defaultTemperature);
            case "max_tokens" -> String.valueOf(defaultMaxTokens);
            case "timeout_ms" -> String.valueOf(defaultTimeoutMs);
            case "retry_times" -> String.valueOf(defaultRetryTimes);
            case "rate_limit_per_day" -> String.valueOf(defaultRateLimit);
            default -> null;
        };
    }

    public String getBaseUrl() {
        return get("base_url");
    }

    public String getApiKey() {
        return get("api_key");
    }

    public static boolean isConfiguredApiKey(String key) {
        return StrUtil.isNotBlank(key)
                && !"sk-xxx".equals(key.trim())
                && !key.trim().startsWith("${");
    }

    public String getModel() {
        return get("model_name");
    }

    public double getTemperature() {
        return Double.parseDouble(get("temperature"));
    }

    public int getMaxTokens() {
        return Integer.parseInt(get("max_tokens"));
    }

    public int getTimeoutMs() {
        return Integer.parseInt(get("timeout_ms"));
    }

    public int getRetryTimes() {
        return Integer.parseInt(get("retry_times"));
    }

    public int getRateLimitPerDay() {
        return Integer.parseInt(get("rate_limit_per_day"));
    }
}
