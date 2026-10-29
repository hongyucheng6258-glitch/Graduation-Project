package com.campus.platform.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** AI 辅助发布-润色请求 */
@Data
public class AssistPolishDTO {

    /** 内容类型：activity 活动 / idle 闲置 / lostfound 失物招领 / post 动态 */
    @NotBlank(message = "内容类型不能为空")
    private String type;

    /** 原文内容 */
    @NotBlank(message = "请粘贴需要润色的内容")
    private String content;

    /** 动作：polish 润色 / expand 扩写 / shorten 精简（默认 polish） */
    private String action;
}
