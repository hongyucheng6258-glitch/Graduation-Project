package com.campus.platform.module.idle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 闲置互评表（Q4 纯撮合+互评）。
 * 联合唯一索引 (appointment_id, from_user_id) 防重复评价。
 */
@Data
@TableName("idle_review")
public class IdleReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appointmentId;

    /** 评价方 */
    private Long fromUserId;

    /** 被评价方 */
    private Long toUserId;

    /** 1-5分 */
    private Integer score;

    private String content;

    private LocalDateTime createTime;
}
