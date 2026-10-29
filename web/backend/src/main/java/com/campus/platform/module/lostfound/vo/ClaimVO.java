package com.campus.platform.module.lostfound.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认领申请视图（含申请人信息）。
 */
@Data
public class ClaimVO {

    private Long id;

    private Long lostFoundId;

    private Long claimUserId;

    private String claimNickname;

    private String claimAvatar;

    /** 认领说明 */
    private String message;

    /** 联系方式 */
    private String contact;

    /** 0待确认 1已同意 2已拒绝 3已归还 */
    private Integer status;

    private LocalDateTime createTime;
}
