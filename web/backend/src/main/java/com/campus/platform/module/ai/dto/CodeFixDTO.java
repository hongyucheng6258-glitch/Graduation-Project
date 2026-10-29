package com.campus.platform.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 代码纠错请求。
 */
@Data
public class CodeFixDTO {

    @NotBlank(message = "代码不能为空")
    private String code;

    @NotBlank(message = "语言不能为空")
    private String language;

    private String extra;
}
