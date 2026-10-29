package com.campus.platform.module.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态点赞表（联合唯一索引防重复点赞）。
 */
@Data
@TableName("post_like")
public class PostLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    private Long userId;

    private LocalDateTime createTime;
}
