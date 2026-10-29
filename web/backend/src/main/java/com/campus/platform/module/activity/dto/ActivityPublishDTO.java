package com.campus.platform.module.activity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动发布请求。
 */
@Data
public class ActivityPublishDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题最长64字")
    private String title;

    private String description;

    private List<String> images;

    private String category;

    private String location;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signupDeadline;

    @Min(value = 0, message = "人数上限不能为负数")
    private Integer maxMembers;
}
