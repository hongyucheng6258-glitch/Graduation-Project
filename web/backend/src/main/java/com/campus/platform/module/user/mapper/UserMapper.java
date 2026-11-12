package com.campus.platform.module.user.mapper;

import com.campus.platform.module.user.entity.User;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.platform.common.DailyCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("""
            SELECT DATE(create_time) AS day, COUNT(*) AS total
            FROM `user`
            WHERE create_time >= #{start} AND create_time < #{end}
            GROUP BY DATE(create_time)
            """)
    List<DailyCount> countByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
