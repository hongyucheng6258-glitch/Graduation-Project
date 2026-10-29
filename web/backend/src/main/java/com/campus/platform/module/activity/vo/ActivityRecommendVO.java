package com.campus.platform.module.activity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 活动智能推荐结果 */
@Data
public class ActivityRecommendVO {

    private Long id;
    private String title;
    private String category;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 已通过报名数 */
    private Long memberCount;
    /** AI 推荐理由 */
    private String reason;
}
