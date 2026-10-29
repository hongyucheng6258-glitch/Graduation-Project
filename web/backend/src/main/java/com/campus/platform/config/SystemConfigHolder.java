package com.campus.platform.config;

import cn.hutool.core.util.StrUtil;
import com.campus.platform.module.site.entity.SystemConfig;
import com.campus.platform.module.site.mapper.SystemConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 通用系统配置持有者：DB 值加载到内存，修改后调 {@link #refresh()} 免重启即时生效。
 * <p>
 * 优先级：① system_config 表非空值 → ② {@link #DEFAULTS} 内置默认值
 * <p>
 * 所有需要在线可配的系统参数都应通过本类读取，不要直接读 DB 或硬编码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemConfigHolder {

    private final SystemConfigMapper systemConfigMapper;

    /** 内置默认值（DB 中无记录或值为空时回退） */
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("site_name", "AI校园综合服务平台"),
            Map.entry("site_slogan", "智慧校园，一站式服务"),
            Map.entry("maintenance_mode", "false"),
            Map.entry("register_enabled", "true"),
            Map.entry("post_publish_enabled", "true"),
            Map.entry("idle_publish_enabled", "true"),
            Map.entry("activity_publish_enabled", "true"),
            Map.entry("audit_high_risk_words", "转账,押金,银行卡,刷单,兼职返利,加微信,二维码,代充,账号交易"),
            Map.entry("audit_medium_risk_words", "悬赏,收费,校外,联系我,手机号,群聊,购买"),
            Map.entry("ai_audit_enabled", "false"),
            Map.entry("chat_rate_per_sec", "5"),
            Map.entry("chat_rate_per_min", "100"),
            Map.entry("chat_message_max_length", "2000"),
            Map.entry("upload_max_size_mb", "20"),
            Map.entry("upload_image_max_count", "9"),
            Map.entry("user_default_status", "0"),
            Map.entry("login_fail_lock_threshold", "5")
    );

    // volatile + 整体换引用：refresh 期间读者要么看到完整旧快照，要么看到完整新快照，绝无中间态（缓存一致）
    private volatile Map<String, String> configCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    /** 从 DB 重新加载全部配置到缓存（修改配置后调用即生效） */
    public void refresh() {
        Map<String, String> next = new ConcurrentHashMap<>();
        List<SystemConfig> configs = systemConfigMapper.selectList(null);
        for (SystemConfig config : configs) {
            if (StrUtil.isNotBlank(config.getConfigKey())) {
                next.put(config.getConfigKey(),
                        config.getConfigValue() == null ? "" : config.getConfigValue());
            }
        }
        // 先构建完整快照再整体发布，避免 clear+put 过程中被读到半空缓存
        configCache = next;
        log.info("SystemConfigHolder 已刷新，共加载 {} 项配置", configCache.size());
    }

    /** 获取字符串配置：DB 非空值 → 内置默认 → null */
    public String get(String key) {
        String val = configCache.get(key);
        if (StrUtil.isNotBlank(val)) {
            return val;
        }
        return DEFAULTS.get(key);
    }

    /** R6：是否为内置关键配置键（内置键不可删除，只能改值） */
    public boolean isBuiltinKey(String key) {
        return key != null && DEFAULTS.containsKey(key);
    }

    /** 获取 int 配置，解析失败或不存在时返回默认值 */
    public int getInt(String key, int defaultValue) {
        String val = get(key);
        if (StrUtil.isBlank(val)) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            log.warn("系统配置 {} 不是合法 int: {}", key, val);
            return defaultValue;
        }
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    /** 获取 long 配置 */
    public long getLong(String key, long defaultValue) {
        String val = get(key);
        if (StrUtil.isBlank(val)) return defaultValue;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            log.warn("系统配置 {} 不是合法 long: {}", key, val);
            return defaultValue;
        }
    }

    /** 获取 boolean 配置（true/1/yes/on 视为 true） */
    public boolean getBool(String key) {
        String val = get(key);
        if (StrUtil.isBlank(val)) return false;
        val = val.trim().toLowerCase();
        return "true".equals(val) || "1".equals(val) || "yes".equals(val) || "on".equals(val);
    }

    /** 获取 double 配置 */
    public double getDouble(String key, double defaultValue) {
        String val = get(key);
        if (StrUtil.isBlank(val)) return defaultValue;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            log.warn("系统配置 {} 不是合法 double: {}", key, val);
            return defaultValue;
        }
    }

    /** 获取 list 配置（逗号分隔，自动去空白、去空项） */
    public List<String> getList(String key) {
        String val = get(key);
        if (StrUtil.isBlank(val)) return List.of();
        return Arrays.stream(val.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ---------- 常用快捷方法 ----------

    public String getSiteName() {
        return get("site_name");
    }

    public boolean isMaintenanceMode() {
        return getBool("maintenance_mode");
    }

    public boolean isRegisterEnabled() {
        return getBool("register_enabled");
    }

    public boolean isPostPublishEnabled() {
        return getBool("post_publish_enabled");
    }

    public boolean isIdlePublishEnabled() {
        return getBool("idle_publish_enabled");
    }

    public boolean isActivityPublishEnabled() {
        return getBool("activity_publish_enabled");
    }

    public boolean isAiAuditEnabled() {
        return getBool("ai_audit_enabled");
    }

    public int getChatRatePerSec() {
        return getInt("chat_rate_per_sec", 5);
    }

    public int getChatRatePerMin() {
        return getInt("chat_rate_per_min", 100);
    }

    public int getChatMessageMaxLength() {
        return getInt("chat_message_max_length", 2000);
    }

    public int getUploadMaxSizeMb() {
        return getInt("upload_max_size_mb", 20);
    }

    public List<String> getAuditHighRiskWords() {
        return getList("audit_high_risk_words");
    }

    public List<String> getAuditMediumRiskWords() {
        return getList("audit_medium_risk_words");
    }
}
