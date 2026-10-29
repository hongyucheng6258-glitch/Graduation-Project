package com.campus.platform.module.qa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 回答请求 */
@Data
public class AnswerDTO {

    @NotBlank(message = "回答内容不能为空")
    private String content;
}
