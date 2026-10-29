package com.campus.platform.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 管理员新增/修改请求。
 */
@Data
public class AdminSaveDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String nickname;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "super|audit", message = "角色只能为super或audit")
    private String role;
}
