package com.campus.platform.module.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动报名成员表：activity_id + user_id 唯一。
 */
@Data
@TableName("activity_member")
public class ActivityMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long userId;

    private String remark;

    /** 0待审批 1已通过 2已拒绝 */
    private Integer status;

    private LocalDateTime createTime;
}
