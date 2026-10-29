package com.campus.platform.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话表。
 */
@Data
@TableName("ai_session")
public class AiSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 场景：chat/pdf/code/outline */
    private String scene;

    private String title;

    /** 关联PDF文档（pdf场景），可空 */
    private Long docId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
