package com.campus.platform.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 生成练习题记录（第三阶段）：先作为练习，不直接污染错题本。
 */
@Data
@TableName("wrong_question_generated")
public class WrongQuestionGenerated {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 来源错题 */
    private Long wrongQuestionId;

    /** 练习题题目 */
    private String question;

    /** 选项 JSON 数组字符串（选择题），如 ["A. xx","B. xx"] */
    private String options;

    /** 正确答案 */
    private String answer;

    /** 解析 */
    private String analysis;

    /** 0练习中 1已加入错题本 */
    private Integer status;

    private LocalDateTime createTime;
}
