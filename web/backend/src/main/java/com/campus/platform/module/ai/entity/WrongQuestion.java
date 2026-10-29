package com.campus.platform.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题本（B6/B7，v2：快速收录 + 复习闭环）。
 */
@Data
@TableName("wrong_question")
public class WrongQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 学科（空 = 待整理） */
    private String subject;

    /** 标签 */
    private String tag;

    /** 题目 */
    private String question;

    /** 正确答案 */
    private String correctAnswer;

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

    /** AI整理状态 0未整理 1整理失败 2已整理 */
    private Integer analyzeStatus;

    /** 掌握状态 0待复习 1复习中 2基本掌握 3已掌握 */
    private Integer status;

    /** 掌握度 0-100 */
    private Integer masteryScore;

    /** 复习次数 */
    private Integer reviewCount;

    /** 错误次数 */
    private Integer wrongCount;

    /** 连续答对次数 */
    private Integer consecutiveCorrectCount;

    /** 最近复习时间 */
    private LocalDateTime lastReviewTime;

    /** 下次复习时间 */
    private LocalDateTime nextReviewTime;

    /** manual手动 / ai自动 */
    private String source;

    private LocalDateTime createTime;
}
