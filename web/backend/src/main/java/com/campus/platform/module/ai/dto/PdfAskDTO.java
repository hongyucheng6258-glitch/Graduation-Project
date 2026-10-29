package com.campus.platform.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * PDF 文档问答请求。
 */
@Data
public class PdfAskDTO {

    @NotNull(message = "文档ID不能为空")
    private Long docId;

    private Long sessionId;

    @NotBlank(message = "问题不能为空")
    private String question;
}
