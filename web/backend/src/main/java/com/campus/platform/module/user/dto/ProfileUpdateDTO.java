package com.campus.platform.module.user.dto;

import lombok.Data;

/**
 * 个人资料修改请求。
 */
@Data
public class ProfileUpdateDTO {

    private String nickname;

    private String avatar;

    private Integer gender;

    private String bio;

    private String phone;
}
