package com.campus.platform.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 错题录入/更新请求（v2 快速收录：仅题目必填，其余均可选）。
 * <p>
 * 兼容说明：旧端（小程序等）提交 {@code answer} 字段，新端提交 {@code correctAnswer}，
 * 后端统一归一化为正确答案。
 */
@Data
public class WrongQuestionDTO {

    /** 学科（可空，空则保存为「待整理」） */
    private String subject;

    /** 标签 */
    private String tag;

    @NotBlank(message = "题目不能为空")
    private String question;

    /** 正确答案（新字段名） */
    private String correctAnswer;

    /** 正确答案（旧字段名，兼容旧端） */
    private String answer;

    /** 解析 */
    private String analysis;

    /** 我的答案 */
    private String myAnswer;

    /** 错误原因（概念不清/审题错误等） */
    private String errorReason;

    /** 题型（选择/填空/简答等） */
    private String questionType;

    /** 章节 */
    private String chapter;

    /** 难度（易/中/难） */
    private String difficulty;

    /** 知识点（逗号分隔） */
    private String knowledgePoints;

    /** 题目图片URL */
    private String questionImage;

    /** 我的笔记 */
    private String note;

    /** 来源标记（manual手动/ai自动），创建时可传，默认 manual */
    private String source;

    /** 归一化：优先取 correctAnswer，兼容 answer */
    public String resolveCorrectAnswer() {
        if (correctAnswer != null && !correctAnswer.isBlank()) {
            return correctAnswer;
        }
        return answer;
    }
}
