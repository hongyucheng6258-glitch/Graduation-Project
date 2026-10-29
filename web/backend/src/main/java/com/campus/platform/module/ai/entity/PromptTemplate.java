package com.campus.platform.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提示词模板表（B2/D5）。
 */
@Data
@TableName("prompt_template")
public class PromptTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** chat/code_fix/pdf/outline/quiz */
    private String scene;

    private String name;

    /** 模板内容，含 {question} 等占位符 */
    private String content;

    /** 1启用 0停用 */
    private Integer enabled;

    private LocalDateTime updateTime;
}
