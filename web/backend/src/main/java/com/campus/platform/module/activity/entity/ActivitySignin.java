package com.campus.platform.module.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动签到表：activity_id + user_id 唯一，每个活动每人仅签到一次。
 */
@Data
@TableName("activity_signin")
public class ActivitySignin {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long userId;

    private LocalDateTime signTime;
}
