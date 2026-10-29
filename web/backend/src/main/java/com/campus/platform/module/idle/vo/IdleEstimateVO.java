package com.campus.platform.module.idle.vo;

import lombok.Data;

import java.util.List;

/** 闲置 AI 智能估价结果 */
@Data
public class IdleEstimateVO {

    /** 参考最低价（元） */
    private Integer priceMin;
    /** 参考最高价（元） */
    private Integer priceMax;
    /** 行情参考说明 */
    private String reference;
    /** 定价/换物建议 */
    private String tip;
    /** 卖点文案 */
    private List<String> sellingPoints;
}
