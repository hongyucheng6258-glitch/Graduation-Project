package com.campus.platform.module.partner.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 学习搭子发布/匹配请求 */
@Data
public class StudyPartnerDTO {

    @NotBlank(message = "请填写科目/领域")
    private String subject;

    /** 目标：考研/四六级/编程等 */
    private String goal;

    /** 可搭时间 */
    private String schedule;

    /** 自我介绍 */
    private String intro;

    /** 联系方式 */
    private String contact;
}
