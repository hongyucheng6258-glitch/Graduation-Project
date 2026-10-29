package com.campus.platform.module.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 失物认领申请表（C4 认领闭环）。
 */
@Data
@TableName("lost_found_claim")
public class LostFoundClaim {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long lostFoundId;

    /** 认领申请人（失主/捡到者） */
    private Long claimUserId;

    /** 认领说明 */
    private String message;

    /** 联系方式 */
    private String contact;

    /** 0待确认 1已同意 2已拒绝 3已归还 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
