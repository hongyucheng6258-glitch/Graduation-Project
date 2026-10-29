package com.campus.platform.module.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 智能习题生成请求（基于错题）。
 */
@Data
public class QuizDTO {

    @NotNull(message = "错题ID不能为空")
    private Long wrongQuestionId;

    /** 缺答案/解析时是否强制生成（false 默认返回 1011 提示先补充信息） */
    private Boolean force;
}
