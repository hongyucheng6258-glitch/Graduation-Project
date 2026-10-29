package com.campus.platform.module.ai.gateway;

import cn.hutool.core.util.StrUtil;
import com.campus.platform.module.ai.entity.AiConfig;
import com.campus.platform.module.ai.mapper.AiConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 配置持有者：DB 值优先于环境变量/YAML 默认值，修改后调 refresh() 免重启。
 * <p>
 * 优先级：① ai_config 表非空值 → ② 环境变量 → ③ application.yml 默认值
 */
@Component
@RequiredArgsConstructor
public class AiConfigHolder {

    private final AiConfigMapper aiConfigMapper;

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

    private final Map<String, String> configCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    /** 从 DB 重新加载配置到缓存 */
    public void refresh() {
        configCache.clear();
        List<AiConfig> configs = aiConfigMapper.selectList(null);
        for (AiConfig config : configs) {
            if (StrUtil.isNotBlank(config.getConfigValue())) {
                configCache.put(config.getConfigKey(), config.getConfigValue());
            }
        }
    }

    /**
     * 获取配置值：DB 非空值 → 环境变量 → YAML 默认值。
     */
    public String get(String key) {
        String dbVal = configCache.get(key);
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
