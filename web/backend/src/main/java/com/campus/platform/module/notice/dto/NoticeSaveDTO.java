package com.campus.platform.module.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公告保存请求。
 */
@Data
public class NoticeSaveDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题最长64字")
    private String title;

    private String content;

    private String cover;
}
