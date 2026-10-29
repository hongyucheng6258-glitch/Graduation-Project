package com.campus.platform.module.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 活动签到请求。
 */
@Data
public class SigninDTO {

    @NotNull(message = "活动ID不能为空")
    private Long activityId;

    @NotBlank(message = "签到token不能为空")
    private String token;
}
