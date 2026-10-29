package com.campus.platform.module.idle.vo;

import com.campus.platform.module.idle.entity.IdleAppointment;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预约 VO：附带物品标题与对方昵称（我的预约列表用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AppointmentVO extends IdleAppointment {

    private String itemTitle;

    private String itemImage;

    private String buyerNickname;

    private String sellerNickname;

    /** 当前用户是否已评价 */
    private Boolean reviewed;
}
