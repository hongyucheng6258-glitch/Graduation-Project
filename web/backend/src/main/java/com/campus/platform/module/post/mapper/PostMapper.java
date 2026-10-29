package com.campus.platform.module.post.mapper;

import com.campus.platform.module.post.entity.Post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Post Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 *
 * <p>P1 第7项修复：点赞/评论计数改为单行原子 UPDATE（自增减），
 * 避免「先读后写」整行更新在并发下丢失计数；GREATEST(0, …) 保证计数不为负。
 */
@Mapper
public interface PostMapper extends BaseMapper<Post> {

    /** 原子增减点赞数（delta 可为 ±1），计数不小于 0 */
    @Update("UPDATE post SET like_count = GREATEST(0, like_count + #{delta}) WHERE id = #{id}")
    int incrLikeCount(@Param("id") Long id, @Param("delta") int delta);

    /** 原子增减评论数（delta 可为 ±1），计数不小于 0 */
    @Update("UPDATE post SET comment_count = GREATEST(0, comment_count + #{delta}) WHERE id = #{id}")
    int incrCommentCount(@Param("id") Long id, @Param("delta") int delta);
}
