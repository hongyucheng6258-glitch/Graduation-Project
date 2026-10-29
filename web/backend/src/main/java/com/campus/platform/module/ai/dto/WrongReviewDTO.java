package com.campus.platform.module.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 错题复习反馈请求：用户完成一次复习后提交掌握程度，驱动状态机更新。
 */
@Data
public class WrongReviewDTO {

    @NotNull(message = "错题ID不能为空")
    private Long wrongQuestionId;

    /** 本次作答（可空，未作答也算一次复习反馈） */
    private String userAnswer;

    /** 是否答对：0未答对 1答对 */
    @Min(value = 0, message = "isCorrect取值0或1")
    @Max(value = 1, message = "isCorrect取值0或1")
    private Integer isCorrect;

    /** 掌握程度：0仍然不会 1有点理解 2基本掌握 3已完全掌握 */
    @NotNull(message = "请选择掌握程度")
    @Min(value = 0, message = "masteryLevel取值0-3")
    @Max(value = 3, message = "masteryLevel取值0-3")
    private Integer masteryLevel;

    /** 复习备注（可空） */
    private String reviewNote;
}
