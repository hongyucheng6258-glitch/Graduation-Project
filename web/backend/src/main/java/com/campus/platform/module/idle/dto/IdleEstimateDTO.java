package com.campus.platform.module.idle.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 闲置 AI 智能估价请求 */
@Data
public class IdleEstimateDTO {

    /** 物品名称，如：九成新《数据结构》教材 */
    @NotBlank(message = "请填写物品名称")
    private String title;

    /** 物品描述（成色/购入渠道/瑕疵等） */
    private String description;

    /** 分类：教材书籍/数码电子/生活用品/运动器材/服饰鞋包/其他 */
    private String category;

    /** 期望换物（可空） */
    private String expectItem;
}
