package com.campus.platform.module.post.vo;

import com.campus.platform.module.post.entity.PostComment;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评论 VO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommentVO extends PostComment {

    private String nickname;

    private String avatar;
}
