package com.campus.platform.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词库（DFA，启动时与 sensitive-words.txt 合并加载）。
 */
@Data
@TableName("sensitive_word")
public class SensitiveWord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String word;

    private LocalDateTime createTime;
}
