package com.campus.platform.module.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态表（C6）。
 */
@Data
@TableName("post")
public class Post {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String content;

    /** 图片URL JSON数组字符串 */
    private String images;

    private Integer likeCount;

    private Integer commentCount;

    private Integer auditStatus;

    private String auditReason;

    /** AI风险等级：0低风险 1中风险 2高风险 */
    private Integer aiRiskLevel;

    private String aiAuditReason;

    private LocalDateTime aiAuditTime;

    /** manual/ai/ai_manual */
    private String auditSource;

    private LocalDateTime createTime;
}
