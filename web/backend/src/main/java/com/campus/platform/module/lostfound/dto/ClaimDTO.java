package com.campus.platform.module.lostfound.dto;

import lombok.Data;

/**
 * 失物认领申请请求。
 */
@Data
public class ClaimDTO {

    /** 认领说明（如卡号/物品特征） */
    private String message;

    /** 联系方式 */
    private String contact;
}
