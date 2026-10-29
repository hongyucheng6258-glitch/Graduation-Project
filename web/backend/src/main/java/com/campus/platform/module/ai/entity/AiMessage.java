package com.campus.platform.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 消息表。
 */
@Data
@TableName("ai_message")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** user/assistant/system */
    private String role;

    private String content;

    private Integer tokens;

    private LocalDateTime createTime;
}
