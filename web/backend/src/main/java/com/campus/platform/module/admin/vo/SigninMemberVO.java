package com.campus.platform.module.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 签到报表-成员行。
 */
@Data
public class SigninMemberVO {

    private Long userId;

    private String nickname;

    /** 0待审批 1已通过 2已拒绝 */
    private Integer memberStatus;

    /** 是否已签到 */
    private Boolean signed;

    private LocalDateTime signTime;
}
