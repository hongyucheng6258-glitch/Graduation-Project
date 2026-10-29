package com.campus.platform.module.activity.vo;

import com.campus.platform.module.activity.entity.Activity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 活动 VO：图片数组 + 发布者信息 + 已通过报名数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityVO extends Activity {

    private List<String> imageList;

    private String publisherNickname;

    private String publisherAvatar;

    /** 已通过报名数 */
    private Long memberCount;

    // ---------- 有效展示状态（按时间动态计算，不落库） ----------

    /** 有效展示状态：0报名中 1已满员 2报名已截止 3活动进行中 4已结束 5已下架 */
    private Integer displayStatus;

    private String displayStatusText;

    /** 当前是否可报名 */
    private Boolean canSignup;

    /** 不可报名原因（可报名时为空） */
    private String signupDisabledReason;
}
