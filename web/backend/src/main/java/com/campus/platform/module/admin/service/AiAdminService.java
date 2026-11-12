package com.campus.platform.module.admin.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.module.ai.gateway.AiConfigHolder;
import com.campus.platform.common.BizException;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.dto.PromptTemplateDTO;
import com.campus.platform.module.ai.entity.AiCallLog;
import com.campus.platform.module.ai.entity.AiConfig;
import com.campus.platform.module.ai.entity.PromptTemplate;
import com.campus.platform.module.ai.mapper.AiCallLogMapper;
import com.campus.platform.module.ai.mapper.AiConfigMapper;
import com.campus.platform.module.ai.mapper.PromptTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 管理端-AI 配置/模板/日志（D5/D6）。
 * 配置修改后调用 {@link AiConfigHolder#refresh()} 即时生效，无需重启。
 */
@Service
@RequiredArgsConstructor
public class AiAdminService {

    private static final String API_KEY = "api_key";
    private static final String API_KEY_MASK = "********";

    private final AiConfigMapper aiConfigMapper;
    private final PromptTemplateMapper promptTemplateMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final AiConfigHolder aiConfigHolder;

    /** 返回管理端配置，密钥仅用于展示配置状态。 */
    public List<AiConfig> listConfigs() {
        List<AiConfig> configs = new ArrayList<>(aiConfigMapper.selectList(null));
        AiConfig keyConfig = new AiConfig();
        keyConfig.setConfigKey(API_KEY);
        keyConfig.setConfigValue(AiConfigHolder.isConfiguredApiKey(aiConfigHolder.getApiKey()) ? API_KEY_MASK : "");
        for (int index = 0; index < configs.size(); index++) {
            AiConfig config = configs.get(index);
            if (API_KEY.equals(config.getConfigKey())) {
                BeanUtil.copyProperties(config, keyConfig, "configValue");
                configs.set(index, keyConfig);
                return configs;
            }
        }
        configs.add(keyConfig);
        return configs;
    }

    /** 批量修改配置 → 刷新缓存即时生效 */
    public void updateConfigs(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "配置不能为空");
        }
        for (Map.Entry<String, String> e : configs.entrySet()) {
            String value = e.getValue();
            if (API_KEY.equals(e.getKey())) {
                if (StrUtil.isBlank(value) || value.trim().matches("\\*+")) {
                    continue;
                }
                value = value.trim();
            }
            int updated = aiConfigMapper.update(null, new LambdaUpdateWrapper<AiConfig>()
                    .eq(AiConfig::getConfigKey, e.getKey())
                    .set(AiConfig::getConfigValue, value));
            if (updated == 0) {
                // 新 key 直接插入
                AiConfig config = new AiConfig();
                config.setConfigKey(e.getKey());
                config.setConfigValue(value);
                aiConfigMapper.insert(config);
            }
        }
        aiConfigHolder.refresh();
    }

    // ---------- 提示词模板 ----------

    public List<PromptTemplate> listPrompts(String scene) {
        return promptTemplateMapper.selectList(new LambdaQueryWrapper<PromptTemplate>()
                .eq(StrUtil.isNotBlank(scene), PromptTemplate::getScene, scene)
                .orderByDesc(PromptTemplate::getUpdateTime));
    }

    public PromptTemplate createPrompt(PromptTemplateDTO dto) {
        PromptTemplate tpl = new PromptTemplate();
        tpl.setScene(dto.getScene());
        tpl.setName(dto.getName());
        tpl.setContent(dto.getContent());
        tpl.setEnabled(dto.getEnabled());
        promptTemplateMapper.insert(tpl);
        return tpl;
    }

    public PromptTemplate updatePrompt(Long id, PromptTemplateDTO dto) {
        PromptTemplate tpl = promptTemplateMapper.selectById(id);
        if (tpl == null) {
            throw new BizException(ResultCode.NOT_FOUND, "模板不存在");
        }
        tpl.setScene(dto.getScene());
        tpl.setName(dto.getName());
        tpl.setContent(dto.getContent());
        tpl.setEnabled(dto.getEnabled());
        promptTemplateMapper.updateById(tpl);
        return tpl;
    }

    // ---------- 调用日志 ----------

    /** AI 调用日志（用户/场景/结果筛选） */
    public PageResult<AiCallLog> listLogs(Long userId, String scene, Integer status,
                                          int pageNum, int pageSize) {
        Page<AiCallLog> page = aiCallLogMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AiCallLog>()
                        .eq(userId != null, AiCallLog::getUserId, userId)
                        .eq(StrUtil.isNotBlank(scene), AiCallLog::getScene, scene)
                        .eq(status != null, AiCallLog::getStatus, status)
                        .orderByDesc(AiCallLog::getId));
        return PageResult.of(page);
    }
}
