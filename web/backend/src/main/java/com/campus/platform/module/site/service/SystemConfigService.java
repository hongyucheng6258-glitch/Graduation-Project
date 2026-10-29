package com.campus.platform.module.site.service;

import com.campus.platform.module.site.mapper.SystemConfigMapper;
import com.campus.platform.module.site.entity.SystemConfig;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.config.SystemConfigHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 管理端-通用系统配置服务。
 *
 * <p>R6 复查修复：
 * <ul>
 *   <li>缓存刷新移到<b>事务提交成功之后</b>（afterCommit）：事务内 refresh 会让其他请求读到
 *       尚未提交的配置；回滚时也不会留下「缓存新、库旧」的不一致。</li>
 *   <li>明确校验：配置键格式、值类型（string/int/bool/json）、int/bool 值范围、
 *       内置关键键不可删除、updateConfigs 不接受未知键。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private static final Set<String> ALLOWED_VALUE_TYPES = Set.of("string", "int", "bool", "json", "list");
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");
    private static final Map<String, String> BUILTIN_TYPES = Map.ofEntries(
            Map.entry("site_name", "string"), Map.entry("site_slogan", "string"),
            Map.entry("maintenance_mode", "bool"), Map.entry("register_enabled", "bool"),
            Map.entry("post_publish_enabled", "bool"), Map.entry("idle_publish_enabled", "bool"),
            Map.entry("activity_publish_enabled", "bool"), Map.entry("audit_high_risk_words", "list"),
            Map.entry("audit_medium_risk_words", "list"), Map.entry("ai_audit_enabled", "bool"),
            Map.entry("chat_rate_per_sec", "int"), Map.entry("chat_rate_per_min", "int"),
            Map.entry("chat_message_max_length", "int"), Map.entry("upload_max_size_mb", "int"),
            Map.entry("upload_image_max_count", "int"), Map.entry("user_default_status", "int"),
            Map.entry("login_fail_lock_threshold", "int")
    );

    private final SystemConfigMapper systemConfigMapper;
    private final SystemConfigHolder systemConfigHolder;

    /** 读取全部配置（按 category + sort 排序） */
    public List<SystemConfig> listAll() {
        return systemConfigMapper.selectList(new LambdaQueryWrapper<SystemConfig>()
                .orderByAsc(SystemConfig::getCategory)
                .orderByAsc(SystemConfig::getSort));
    }

    /** 按分组读取配置 */
    public List<SystemConfig> listByCategory(String category) {
        return systemConfigMapper.selectList(new LambdaQueryWrapper<SystemConfig>()
                .eq(StrUtil.isNotBlank(category), SystemConfig::getCategory, category)
                .orderByAsc(SystemConfig::getSort));
    }

    /**
     * 批量更新配置值 → 事务提交后刷新缓存即时生效。
     * 只更新 DB 中已存在的键；未知键直接报错（不再静默 insert，避免垃圾配置）。
     */
    @Transactional
    public void updateConfigs(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "配置不能为空");
        }
        for (Map.Entry<String, String> e : configs.entrySet()) {
            String key = e.getKey();
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw new BizException(ResultCode.BAD_REQUEST, "非法配置键：" + key);
            }
            String value = e.getValue() == null ? "" : e.getValue().trim();
            SystemConfig existing = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                    .eq(SystemConfig::getConfigKey, key));
            if (existing == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "未知配置键：" + key + "（请先新增配置项）");
            }
            String builtinType = BUILTIN_TYPES.get(key);
            if (builtinType != null && !builtinType.equals(existing.getValueType())) {
                throw new BizException(ResultCode.BAD_REQUEST, "内置配置项类型已损坏：" + key);
            }
            validateValue(key, existing.getValueType(), value);
            systemConfigMapper.update(null, new LambdaUpdateWrapper<SystemConfig>()
                    .eq(SystemConfig::getConfigKey, key)
                    .set(SystemConfig::getConfigValue, value));
        }
        refreshAfterCommit();
    }

    /** 新增配置项 */
    @Transactional
    public SystemConfig create(SystemConfig config) {
        if (StrUtil.isBlank(config.getConfigKey()) || !KEY_PATTERN.matcher(config.getConfigKey().trim()).matches()) {
            throw new BizException(ResultCode.BAD_REQUEST, "配置键非法（仅允许字母/数字/_.-，≤64 字符）");
        }
        config.setConfigKey(config.getConfigKey().trim());
        Long exists = systemConfigMapper.selectCount(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, config.getConfigKey()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "配置键已存在");
        }
        if (StrUtil.isBlank(config.getValueType())) config.setValueType("string");
        if (!ALLOWED_VALUE_TYPES.contains(config.getValueType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "未知配置类型：" + config.getValueType());
        }
        validateValue(config.getConfigKey(), config.getValueType(), config.getConfigValue());
        if (StrUtil.isBlank(config.getCategory())) config.setCategory("basic");
        if (config.getSort() == null) config.setSort(999);
        systemConfigMapper.insert(config);
        refreshAfterCommit();
        return config;
    }

    /** 更新配置项（含元信息：类型/分组/说明/排序） */
    @Transactional
    public SystemConfig update(Long id, SystemConfig config) {
        SystemConfig existing = systemConfigMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "配置项不存在");
        }
        if (StrUtil.isNotBlank(config.getValueType()) && !ALLOWED_VALUE_TYPES.contains(config.getValueType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "未知配置类型：" + config.getValueType());
        }
        String newType = StrUtil.isBlank(config.getValueType()) ? existing.getValueType() : config.getValueType();
        String builtinType = BUILTIN_TYPES.get(existing.getConfigKey());
        if (builtinType != null && !builtinType.equals(newType)) {
            throw new BizException(ResultCode.BAD_REQUEST, "内置配置项类型不可修改：" + existing.getConfigKey());
        }
        String newValue = config.getConfigValue() == null ? existing.getConfigValue() : config.getConfigValue().trim();
        validateValue(existing.getConfigKey(), newType, newValue);
        existing.setConfigValue(newValue);
        existing.setValueType(newType);
        if (StrUtil.isNotBlank(config.getCategory())) existing.setCategory(config.getCategory());
        if (StrUtil.isNotBlank(config.getDescription())) existing.setDescription(config.getDescription());
        if (config.getSort() != null) existing.setSort(config.getSort());
        systemConfigMapper.updateById(existing);
        refreshAfterCommit();
        return existing;
    }

    /** 删除配置项（内置关键键不可删除） */
    @Transactional
    public void delete(Long id) {
        SystemConfig existing = systemConfigMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "配置项不存在");
        }
        if (systemConfigHolder.isBuiltinKey(existing.getConfigKey())) {
            throw new BizException(ResultCode.BAD_REQUEST, "内置关键配置项不可删除（只能改值）：" + existing.getConfigKey());
        }
        systemConfigMapper.deleteById(id);
        refreshAfterCommit();
    }

    /** 手动刷新缓存（无事务时直接刷；有事务时注册 afterCommit） */
    public void refresh() {
        refreshAfterCommit();
    }

    /**
     * R6：事务提交成功后再刷新缓存。
     * 仍在事务中注册 afterCommit——提交前其他请求读不到未提交配置，回滚也不会污染缓存；
     * 无活动事务时（手动调用）立即刷新。
     */
    private void refreshAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    systemConfigHolder.refresh();
                }
            });
        } else {
            systemConfigHolder.refresh();
        }
    }

    /** 按固定键定义校验类型、整数溢出和业务范围。 */
    private void validateValue(String key, String valueType, String value) {
        String builtinType = BUILTIN_TYPES.get(key);
        String t = builtinType == null ? (valueType == null ? "string" : valueType) : builtinType;
        if (StrUtil.isBlank(value)) {
            if ("string".equals(t) || "json".equals(t) || "list".equals(t)) return;
            throw new BizException(ResultCode.BAD_REQUEST, "配置值不能为空：" + key);
        }
        if ("int".equals(t)) {
            final int parsed;
            try {
                parsed = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new BizException(ResultCode.BAD_REQUEST, "值必须是合法整数：" + value);
            }
            int min = Integer.MIN_VALUE;
            int max = Integer.MAX_VALUE;
            if ("chat_rate_per_sec".equals(key)) { min = 1; max = 60; }
            else if ("chat_rate_per_min".equals(key)) { min = 1; max = 10000; }
            else if ("chat_message_max_length".equals(key)) { min = 1; max = 10000; }
            else if ("upload_max_size_mb".equals(key)) { min = 1; max = 100; }
            else if ("upload_image_max_count".equals(key)) { min = 1; max = 20; }
            else if ("user_default_status".equals(key)) { min = 0; max = 1; }
            else if ("login_fail_lock_threshold".equals(key)) { min = 0; max = 20; }
            if (parsed < min || parsed > max) {
                throw new BizException(ResultCode.BAD_REQUEST, "配置值超出允许范围：" + key + "（" + min + "～" + max + "）");
            }
        }
        if ("bool".equals(t) && !("true".equals(value) || "false".equals(value))) {
            throw new BizException(ResultCode.BAD_REQUEST, "bool 类型值必须为 true/false");
        }
        if ("json".equals(t)) {
            try {
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(value);
            } catch (Exception e) {
                throw new BizException(ResultCode.BAD_REQUEST, "json 类型值不是合法 JSON");
            }
        }
        if ("list".equals(t) && value.length() > 5000) {
            throw new BizException(ResultCode.BAD_REQUEST, "列表配置过长：" + key);
        }
    }
}
