package com.campus.platform.module.activity.vo;

import com.campus.platform.module.activity.entity.ActivityMember;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报名名单 VO（发布者可见）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberVO extends ActivityMember {

    private String nickname;

    private String avatar;

    private String studentNo;

    /** 是否已签到 */
    private Boolean signedIn;

    /** 活动标题（我的报名列表用） */
    private String activityTitle;
}
