package com.campus.platform.module.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 他人主页 · 公开信息卡（脱敏：不返回手机号/完整学号）。
 */
@Data
public class UserProfileVO {

    private Long id;

    private String nickname;

    private String avatar;

    /** 0未知 1男 2女 */
    private Integer gender;

    private String bio;

    /** 年级（如 2021级），不暴露完整学号 */
    private String grade;

    private LocalDateTime createTime;

    /** 已审核发布的闲置数 */
    private Long idleCount;

    /** 已审核动态数 */
    private Long postCount;

    /** 收到的评价数 */
    private Long reviewCount;

    /** 平均评分（1-5，一位小数） */
    private Double avgScore;
}
