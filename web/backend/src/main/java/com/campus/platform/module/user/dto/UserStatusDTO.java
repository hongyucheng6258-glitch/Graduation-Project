package com.campus.platform.module.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户状态变更请求（管理员禁用/启用）。
 */
@Data
public class UserStatusDTO {

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态值无效")
    @Max(value = 1, message = "状态值无效")
    private Integer status;
}
