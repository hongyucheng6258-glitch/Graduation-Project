package com.campus.platform.module.site;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.config.SystemConfigHolder;
import com.campus.platform.module.site.entity.SystemConfig;
import com.campus.platform.module.site.mapper.SystemConfigMapper;
import com.campus.platform.module.site.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {
    @Mock private SystemConfigMapper mapper;
    @Mock private SystemConfigHolder holder;
    @InjectMocks private SystemConfigService service;

    private SystemConfig config(String key, String type, String value) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setValueType(type);
        config.setConfigValue(value);
        return config;
    }

    @Test
    void rejectsNegativeRateBeforeWriting() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config("chat_rate_per_sec", "int", "5"));

        assertThatThrownBy(() -> service.updateConfigs(Map.of("chat_rate_per_sec", "0")))
                .isInstanceOf(BizException.class);

        verify(mapper, org.mockito.Mockito.never()).update(any(), any());
    }

    @Test
    void rejectsBuiltinTypeChange() {
        SystemConfig existing = config("chat_rate_per_sec", "string", "5");
        existing.setId(1L);
        when(mapper.selectById(1L)).thenReturn(existing);

        SystemConfig requested = config("chat_rate_per_sec", "string", "10");
        assertThatThrownBy(() -> service.update(1L, requested))
                .isInstanceOf(BizException.class);

        verify(mapper, org.mockito.Mockito.never()).updateById(any(SystemConfig.class));
    }

    @Test
    void rejectsIntegerOverflow() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config("chat_message_max_length", "int", "2000"));

        assertThatThrownBy(() -> service.updateConfigs(Map.of("chat_message_max_length", "999999999999999999999")))
                .isInstanceOf(BizException.class);

        verify(mapper, org.mockito.Mockito.never()).update(any(), any());
    }
}
