package com.campus.platform.module.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评论发布请求。
 */
@Data
public class CommentDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论最长500字")
    private String content;
}
