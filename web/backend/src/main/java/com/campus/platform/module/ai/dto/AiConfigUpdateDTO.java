package com.campus.platform.module.ai.dto;

import lombok.Data;

import java.util.Map;

/**
 * AI 配置批量修改请求。
 */
@Data
public class AiConfigUpdateDTO {

    private Map<String, String> configs;
}
