package com.campus.platform.module.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 报名审批处理请求。
 */
@Data
public class MemberHandleDTO {

    @NotNull(message = "操作不能为空")
    private Boolean approve;
}
