package com.campus.platform.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** AI 辅助发布-生成草稿请求 */
@Data
public class AssistComposeDTO {

    /** 内容类型：activity 活动 / idle 闲置 / lostfound 失物招领 / post 动态 */
    @NotBlank(message = "内容类型不能为空")
    private String type;

    /** 主题或需求描述（一句话或关键词） */
    @NotBlank(message = "请描述你想发布的内容主题")
    private String topic;

    /** 补充信息（时间/地点/价格等，可空） */
    private String extra;
}
