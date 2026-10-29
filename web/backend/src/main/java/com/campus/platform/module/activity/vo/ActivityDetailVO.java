package com.campus.platform.module.activity.vo;


import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 活动详情 VO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityDetailVO extends ActivityVO {

    /** 是否本人发布 */
    private Boolean isOwner;

    /** 当前用户报名状态：null未报名 0待审批 1已通过 2已拒绝 */
    private Integer mySignupStatus;

    /** 当前用户是否已签到 */
    private Boolean signedIn;
}
