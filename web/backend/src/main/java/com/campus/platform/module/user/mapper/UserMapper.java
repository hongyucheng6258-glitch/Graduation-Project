package com.campus.platform.module.user.mapper;

import com.campus.platform.module.user.entity.User;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.platform.common.DailyCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * User Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    default Map<Long, User> findPublisherProfiles(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return selectList(new LambdaQueryWrapper<User>()
                .select(User::getId, User::getNickname, User::getAvatar)
                .in(User::getId, ids))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    @Select("""
            SELECT DATE(create_time) AS day, COUNT(*) AS total
            FROM `user`
            WHERE create_time >= #{start} AND create_time < #{end}
            GROUP BY DATE(create_time)
            """)
    List<DailyCount> countByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
