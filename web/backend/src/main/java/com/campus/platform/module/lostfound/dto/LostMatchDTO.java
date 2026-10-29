package com.campus.platform.module.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 失物 AI 智能匹配请求 */
@Data
public class LostMatchDTO {

    /** 丢失物品标题，如：图书馆丢失黑色钱包 */
    @NotBlank(message = "请填写丢失物品标题")
    private String title;

    /** 丢失物品描述（特征/时间/地点等） */
    private String description;
}
