package com.campus.platform.module.post.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 动态发布请求。
 */
@Data
public class PostPublishDTO {

    @NotBlank(message = "内容不能为空")
    private String content;

    private List<String> images;
}
