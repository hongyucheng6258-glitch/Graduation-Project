package com.campus.platform.module.idle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 闲置预约表（C1 闭环：预约→接受/拒绝→完成→互评）。
 */
@Data
@TableName("idle_appointment")
public class IdleAppointment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itemId;

    /** 预约方（买家/换物发起方） */
    private Long buyerId;

    /** 物品所属方（卖家） */
    private Long sellerId;

    /** 预约留言 */
    private String message;

    /** 0待确认 1已接受 2已拒绝 3已完成 4已取消 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
