package com.campus.platform.module.ai.vo;

import lombok.Data;

/**
 * 错题本顶部数据概览。
 */
@Data
public class WrongQuestionStatsVO {

    /** 错题总数 */
    private Long total;

    /** 待复习 */
    private Long pending;

    /** 复习中 */
    private Long reviewing;

    /** 基本掌握 */
    private Long basic;

    /** 已掌握 */
    private Long mastered;

    /** 本周复习次数 */
    private Long weekReviewCount;

    /** 今日待复习 */
    private Long todayPending;
}
