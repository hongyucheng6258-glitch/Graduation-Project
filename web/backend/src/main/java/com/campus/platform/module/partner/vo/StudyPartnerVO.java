package com.campus.platform.module.partner.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 学习搭子视图 */
@Data
public class StudyPartnerVO {

    private Long id;
    private Long userId;
    private String subject;
    private String goal;
    private String schedule;
    private String intro;
    private String contact;
    private Integer status;
    private LocalDateTime createTime;
    private String publisherNickname;
    private String publisherAvatar;
    /** 是否为本人发布 */
    private Boolean isOwner;
    /** AI 匹配理由 */
    private String reason;
}
