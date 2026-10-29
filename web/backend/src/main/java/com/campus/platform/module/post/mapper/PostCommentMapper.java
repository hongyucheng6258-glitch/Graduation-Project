package com.campus.platform.module.post.mapper;

import com.campus.platform.module.post.entity.PostComment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * PostComment Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface PostCommentMapper extends BaseMapper<PostComment> {
}
