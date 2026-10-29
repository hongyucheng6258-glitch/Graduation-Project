package com.campus.platform.module.idle.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 预约处理请求（接受/拒绝）。
 */
@Data
public class AppointHandleDTO {

    @NotNull(message = "操作不能为空")
    private Boolean accept;
}
