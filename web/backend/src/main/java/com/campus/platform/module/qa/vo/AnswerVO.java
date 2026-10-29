package com.campus.platform.module.qa.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 回答视图 */
@Data
public class AnswerVO {

    private Long id;
    private Long questionId;
    private Long userId;
    private String content;
    private Integer isAccepted;
    private LocalDateTime createTime;
    private String answererNickname;
    private String answererAvatar;
}
