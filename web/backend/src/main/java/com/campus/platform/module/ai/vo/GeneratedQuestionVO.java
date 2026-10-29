package com.campus.platform.module.ai.vo;

import lombok.Data;

import java.util.List;

/**
 * AI 生成练习题（结构化，练习模式用）。
 */
@Data
public class GeneratedQuestionVO {

    /** 练习题记录 ID */
    private Long id;

    /** 来源错题 ID */
    private Long wrongQuestionId;

    /** 题目 */
    private String question;

    /** 选项（选择题；填空/简答为空数组） */
    private List<String> options;

    /** 正确答案（选择题为选项字母如 A） */
    private String answer;

    /** 解析 */
    private String analysis;
}
