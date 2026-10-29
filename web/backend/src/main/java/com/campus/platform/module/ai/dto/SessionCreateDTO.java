package com.campus.platform.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * AI 会话创建请求。
 */
@Data
public class SessionCreateDTO {

    @NotBlank(message = "场景不能为空")
    @Pattern(regexp = "chat|pdf|code_fix|outline|quiz", message = "场景无效")
    private String scene;

    private String title;

    private Long docId;
}
