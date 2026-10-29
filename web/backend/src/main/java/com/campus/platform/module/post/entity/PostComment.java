package com.campus.platform.module.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态评论表（DFA机审 + 举报兜底，Q3）。
 */
@Data
@TableName("post_comment")
public class PostComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    private Long userId;

    private String content;

    /** 0正常 1命中敏感词隐藏 */
    private Integer status;

    private LocalDateTime createTime;
}
