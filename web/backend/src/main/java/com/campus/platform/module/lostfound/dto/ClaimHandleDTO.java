package com.campus.platform.module.lostfound.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 认领申请处理请求。
 */
@Data
public class ClaimHandleDTO {

    @NotNull(message = "操作不能为空")
    private Boolean accept;
}
