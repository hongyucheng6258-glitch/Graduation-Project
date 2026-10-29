package com.campus.platform.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提示词模板保存请求。
 */
@Data
public class PromptTemplateDTO {

    @NotBlank(message = "场景不能为空")
    private String scene;

    @NotBlank(message = "模板名不能为空")
    private String name;

    @NotBlank(message = "模板内容不能为空")
    private String content;

    private Integer enabled;
}
