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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AiAdminService 服务接口。
 */
public interface IAiAdminService {

    List<AiConfig> listConfigs();

    void updateConfigs(Map<String, String> configs);

    List<PromptTemplate> listPrompts(String scene);

    PromptTemplate createPrompt(PromptTemplateDTO dto);

    PromptTemplate updatePrompt(Long id, PromptTemplateDTO dto);

    PageResult<AiCallLog> listLogs(Long userId, String scene, Integer status, int pageNum, int pageSize);
}
