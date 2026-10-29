package com.campus.platform.module.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动表（C2）：学生发起、需审核的线下活动。
 */
@Data
@TableName("activity")
public class Activity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起人 */
    private Long userId;

    private String title;

    private String description;

    /** 图片URL JSON数组字符串 */
    private String images;

    private String category;

    private String location;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime signupDeadline;

    /** 0表示不限人数 */
    private Integer maxMembers;

    /** 0待审核 1通过 2驳回 */
    private Integer auditStatus;

    private String auditReason;

    /** AI风险等级：0低风险 1中风险 2高风险 */
    private Integer aiRiskLevel;

    private String aiAuditReason;

    private LocalDateTime aiAuditTime;

    /** manual/ai/ai_manual */
    private String auditSource;

    /** 0报名中 1已满员 2已结束 3已下架 */
    private Integer status;

    private LocalDateTime createTime;
}
