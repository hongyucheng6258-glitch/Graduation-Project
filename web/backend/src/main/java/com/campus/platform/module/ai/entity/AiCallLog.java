package com.campus.platform.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用日志表（D6 可追溯）。
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** chat/pdf/code_fix/outline/quiz */
    private String scene;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    /** 耗时毫秒 */
    private Integer costMs;

    /** 0成功 1失败 */
    private Integer status;

    private String errorMsg;

    private LocalDateTime createTime;
}
