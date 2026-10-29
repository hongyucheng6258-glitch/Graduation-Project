package com.campus.platform.module.idle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 闲置物品发布请求。
 */
@Data
public class IdlePublishDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题最长64字")
    private String title;

    private String description;

    private List<String> images;

    private String expectItem;

    private String category;
}
