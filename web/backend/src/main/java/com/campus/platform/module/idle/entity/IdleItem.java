package com.campus.platform.module.idle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 闲置物品表（C1）。
 */
@Data
@TableName("idle_item")
public class IdleItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布者 */
    private Long userId;

    private String title;

    private String description;

    /** 图片URL JSON数组字符串 */
    private String images;

    /** 期望换物 */
    private String expectItem;

    private String category;

    /** 0待审核 1通过 2驳回 */
    private Integer auditStatus;

    private String auditReason;

    /** AI风险等级：0低风险 1中风险 2高风险 */
    private Integer aiRiskLevel;

    private String aiAuditReason;

    private LocalDateTime aiAuditTime;

    /** manual/ai/ai_manual */
    private String auditSource;

    /** 0在架 1已预约 2已完成 3已下架 */
    private Integer status;

    private Integer viewCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
