package com.campus.platform.module.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 举报请求。
 */
@Data
public class ReportDTO {

    @NotBlank(message = "举报对象类型不能为空")
    @Pattern(regexp = "idle|activity|lostfound|post|comment|user", message = "举报类型无效")
    private String targetType;

    @NotNull(message = "举报对象ID不能为空")
    private Long targetId;

    @NotBlank(message = "举报类型不能为空")
    private String reasonType;

    private String reason;
}
