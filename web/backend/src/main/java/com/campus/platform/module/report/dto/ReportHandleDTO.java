package com.campus.platform.module.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 举报处置请求。
 */
@Data
public class ReportHandleDTO {

    @NotBlank(message = "处置动作不能为空")
    @Pattern(regexp = "offline|warn|ban|ignore", message = "处置动作无效")
    private String action;

    private String handleResult;
}
