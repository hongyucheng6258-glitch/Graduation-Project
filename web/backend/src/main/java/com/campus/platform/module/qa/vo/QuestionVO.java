package com.campus.platform.module.qa.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 问题视图 */
@Data
public class QuestionVO {

    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String category;
    private Integer status;
    private Long acceptedAnswerId;
    private Integer viewCount;
    private LocalDateTime createTime;
    private String publisherNickname;
    private String publisherAvatar;
    /** 回答数 */
    private Long answerCount;
    /** 是否本人提问 */
    private Boolean isOwner;
}
