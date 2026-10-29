package com.campus.platform.module.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 他人主页 · 收到的评价（闲置互评聚合）。
 */
@Data
public class UserReviewVO {

    private Long id;

    /** 1-5分 */
    private Integer score;

    private String content;

    /** 评价人 */
    private Long fromUserId;

    private String fromNickname;

    private String fromAvatar;

    /** 关联商品 */
    private Long itemId;

    private String itemTitle;

    private LocalDateTime createTime;
}
