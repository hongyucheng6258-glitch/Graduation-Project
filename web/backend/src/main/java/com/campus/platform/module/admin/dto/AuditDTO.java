package com.campus.platform.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审核处理请求。
 */
@Data
public class AuditDTO {

    @NotBlank(message = "驳回理由不能为空")
    @Size(max = 255, message = "驳回理由不能超过255个字符")
    private String reason;
}
