package com.campus.platform.module.qa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 提问/回答请求 */
@Data
public class QuestionDTO {

    @NotBlank(message = "请填写问题标题")
    private String title;

    /** 详细描述 */
    private String content;

    /** 分类：课程/考试/技术/生活/其他 */
    private String category;
}
