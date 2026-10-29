package com.campus.platform.module.lostfound.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 失物招领发布请求。
 */
@Data
public class LostFoundPublishDTO {

    @NotNull(message = "类型不能为空")
    @Min(value = 0, message = "类型无效")
    @Max(value = 1, message = "类型无效")
    private Integer type;

    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题最长64字")
    private String title;

    private String description;

    private List<String> images;

    private String location;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime happenTime;

    private String contact;
}
