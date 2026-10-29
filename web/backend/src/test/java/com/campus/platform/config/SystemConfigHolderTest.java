package com.campus.platform.config;

import com.campus.platform.module.site.entity.SystemConfig;
import com.campus.platform.module.site.mapper.SystemConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 第12项 系统配置缓存一致：DB 值加载、内置默认回退、重复刷新读到最新快照。
 * refresh() 采用“先构建完整快照再整体换引用”，杜绝 clear+put 半空缓存中间态。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SystemConfigHolder 配置缓存一致")
class SystemConfigHolderTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    @InjectMocks
    private SystemConfigHolder holder;

    private SystemConfig cfg(String key, String value) {
        SystemConfig c = new SystemConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        return c;
    }

    @Test
    @DisplayName("刷新后读 DB 值优先于内置默认")
    void refresh_shouldLoadDbValue() {
        when(systemConfigMapper.selectList(null)).thenReturn(List.of(cfg("site_name", "测试校园")));
        holder.refresh();
        assertThat(holder.getSiteName()).isEqualTo("测试校园");
    }

    @Test
    @DisplayName("DB 缺失/空值回退内置默认，未知键返回 null")
    void blankOrMissing_shouldFallbackToDefault() {
        when(systemConfigMapper.selectList(null)).thenReturn(List.of(cfg("maintenance_mode", "")));
        holder.refresh();
        assertThat(holder.isMaintenanceMode()).isFalse();
        assertThat(holder.get("register_enabled")).isEqualTo("true");
        assertThat(holder.get("not_exist_key")).isNull();
    }

    @Test
    @DisplayName("重复刷新读到的是新快照（缓存一致，无残留旧值）")
    void refreshTwice_shouldSeeLatestSnapshot() {
        when(systemConfigMapper.selectList(null))
                .thenReturn(List.of(cfg("site_name", "旧名称")))
                .thenReturn(List.of(cfg("site_name", "新名称")));
        holder.refresh();
        assertThat(holder.getSiteName()).isEqualTo("旧名称");
        holder.refresh();
        assertThat(holder.getSiteName()).isEqualTo("新名称");
    }

    @Test
    @DisplayName("getBool/getInt 解析 DB 值正常，缺失时用调用方默认值")
    void typedGetters_shouldWork() {
        when(systemConfigMapper.selectList(null)).thenReturn(List.of(
                cfg("chat_rate_per_min", "200"),
                cfg("maintenance_mode", "true")));
        holder.refresh();
        assertThat(holder.getChatRatePerMin()).isEqualTo(200);
        assertThat(holder.isMaintenanceMode()).isTrue();
        assertThat(holder.getInt("chat_rate_per_sec", 5)).isEqualTo(5);
    }
}
