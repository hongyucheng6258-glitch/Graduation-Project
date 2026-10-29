package com.campus.platform.module.admin.vo;

import lombok.Data;

import java.util.List;

/**
 * 活动签到报表。
 */
@Data
public class SigninReportVO {

    private Long activityId;

    private String title;

    /** 已通过报名人数 */
    private Integer joinedCount;

    /** 已签到人数 */
    private Integer signinCount;

    /** 签到率（0-100，两位小数） */
    private Double signinRate;

    private List<SigninMemberVO> members;
}
