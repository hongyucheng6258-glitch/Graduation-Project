package com.campus.platform.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 配置键值表（D5，改后即时生效）。
 */
@Data
@TableName("ai_config")
public class AiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configKey;

    private String configValue;

    private String description;

    private LocalDateTime updateTime;
}
