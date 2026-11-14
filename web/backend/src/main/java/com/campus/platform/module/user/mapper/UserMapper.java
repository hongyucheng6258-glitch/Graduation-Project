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
    @org.apache.ibatis.annotations.Update("""
            UPDATE `user` SET password=#{password}, temporary_password_expires_at=#{expiresAt,jdbcType=TIMESTAMP}
            WHERE id=#{id} AND password <=> #{previousPassword,jdbcType=VARCHAR}
            """)
    int replacePassword(@Param("id") Long id, @Param("previousPassword") String previousPassword,
                        @Param("password") String password, @Param("expiresAt") LocalDateTime expiresAt);

    @org.apache.ibatis.annotations.Update("""
            UPDATE `user` SET last_login_time=#{loginTime}
            WHERE id=#{id} AND password=#{password} AND status=0
              AND (temporary_password_expires_at IS NULL OR temporary_password_expires_at>UTC_TIMESTAMP())
            """)
    int recordLogin(@Param("id") Long id, @Param("password") String password,
                    @Param("loginTime") LocalDateTime loginTime);

    @Select("SELECT EXISTS(SELECT 1 FROM `user` WHERE id=#{id} AND temporary_password_expires_at IS NOT NULL)")
    boolean requiresPasswordChange(@Param("id") Long id);

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

    // Historical login events are not stored; last_login_time can only contribute its latest day.
    @Select("""
            SELECT day, COUNT(DISTINCT user_id) AS total
            FROM (
                SELECT id AS user_id, DATE(last_login_time) AS day FROM `user`
                WHERE last_login_time >= #{start} AND last_login_time < #{end}
                UNION ALL
                SELECT id AS user_id, DATE(create_time) AS day FROM `user`
                WHERE create_time >= #{start} AND create_time < #{end}
                UNION ALL
                SELECT user_id AS user_id, DATE(create_time) AS day FROM ai_call_log
                WHERE create_time >= #{start} AND create_time < #{end}
                UNION ALL
                SELECT user_id AS user_id, DATE(create_time) AS day FROM idle_item
                WHERE create_time >= #{start} AND create_time < #{end}
                UNION ALL
                SELECT user_id AS user_id, DATE(create_time) AS day FROM activity
                WHERE create_time >= #{start} AND create_time < #{end}
                UNION ALL
                SELECT user_id AS user_id, DATE(create_time) AS day FROM lost_found
                WHERE create_time >= #{start} AND create_time < #{end}
                UNION ALL
                SELECT user_id AS user_id, DATE(create_time) AS day FROM post
                WHERE create_time >= #{start} AND create_time < #{end}
                UNION ALL
                SELECT user_id AS user_id, DATE(create_time) AS day FROM study_partner
                WHERE create_time >= #{start} AND create_time < #{end}
            ) AS activity_events
            GROUP BY day
            """)
    List<DailyCount> countObservedActiveByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
