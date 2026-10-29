package com.campus.platform.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** AI 校园向导-提问请求 */
@Data
public class GuideAskDTO {

    /** 学生问题，如"周末有什么活动""我报名的活动" */
    @NotBlank(message = "问题不能为空")
    private String question;
}
