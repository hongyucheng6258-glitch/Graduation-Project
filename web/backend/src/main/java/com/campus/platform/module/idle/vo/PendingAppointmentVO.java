package com.campus.platform.module.idle.vo;

import lombok.Data;

/**
 * 卖家待处理预约 VO：详情页展示"接受/拒绝"操作所需的最小信息。
 */
@Data
public class PendingAppointmentVO {

    /** 预约记录ID（处理接口用） */
    private Long appointmentId;

    /** 预约状态：0待确认 1已接受（0 显示接受/拒绝，1 显示确认完成） */
    private Integer status;

    /** 买家昵称 */
    private String buyerNickname;

    /** 买家头像 */
    private String buyerAvatar;

    /** 买家留言 */
    private String message;
}
