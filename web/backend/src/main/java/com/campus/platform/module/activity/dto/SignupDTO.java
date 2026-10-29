package com.campus.platform.module.activity.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 活动报名请求。
 */
@Data
public class SignupDTO {

    @Size(max = 255, message = "报名说明最长255字")
    private String remark;
}
