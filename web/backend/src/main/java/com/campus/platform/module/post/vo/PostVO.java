package com.campus.platform.module.post.vo;

import com.campus.platform.module.post.entity.Post;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 动态 VO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostVO extends Post {

    private List<String> imageList;

    private String nickname;

    private String avatar;

    /** 当前用户是否已点赞 */
    private Boolean liked;

    /** 当前用户是否已收藏 */
    private Boolean favorited;
}
