package com.campus.platform.module.partner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 学习搭子信息 */
@Data
@TableName("study_partner")
public class StudyPartner {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 科目/领域 */
    private String subject;
    /** 目标：考研/四六级/编程等 */
    private String goal;
    /** 可搭时间 */
    private String schedule;
    /** 自我介绍 */
    private String intro;
    /** 联系方式 */
    private String contact;
    /** 0匹配中 1已找到 9已下架 */
    private Integer status;
    /** 0待审 1通过 2驳回 */
    private Integer auditStatus;
    private String auditReason;
    /** AI 风险等级：0低 1中 2高 */
    private Integer aiRiskLevel;
    private String aiAuditReason;
    private LocalDateTime aiAuditTime;
    /** 审核来源：manual / ai / ai_manual */
    private String auditSource;
    private LocalDateTime createTime;
}
