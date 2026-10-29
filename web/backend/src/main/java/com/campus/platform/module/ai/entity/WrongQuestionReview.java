package com.campus.platform.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题复习记录（v2 新增）：统计反复做错、薄弱知识点、每周复习情况。
 */
@Data
@TableName("wrong_question_review")
public class WrongQuestionReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long wrongQuestionId;

    /** 本次作答 */
    private String userAnswer;

    /** 0未答对 1答对 */
    private Integer isCorrect;

    /** 0仍然不会 1有点理解 2基本掌握 3已完全掌握 */
    private Integer masteryLevel;

    /** 复习备注 */
    private String reviewNote;

    private LocalDateTime reviewTime;
}
