package com.campus.platform.module.site.entity;

import com.campus.platform.module.ai.entity.AiConfig;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用系统配置表（管理端在线修改，立即生效，免重启）。
 * <p>
 * 与 {@link AiConfig} 的区别：本表覆盖整个系统的可配置项（站点、审核、聊天、上传、安全等），
 * 而 ai_config 仅覆盖 AI 网关参数。两者均通过各自的 Holder 实现热更新。
 */
@Data
@TableName("system_config")
public class SystemConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configKey;

    private String configValue;

    /** 值类型：string/int/long/double/bool/json/list */
    private String valueType;

    private String description;

    /** 分组：basic/site/security/audit/chat/upload */
    private String category;

    private Integer sort;

    private LocalDateTime updateTime;
}
