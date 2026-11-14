package com.campus.platform.module.ai.mapper;

import com.campus.platform.module.ai.agent.ActivitySignupActions.Card;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivitySignupActionMapper {
    @Insert("""
            INSERT INTO agent_action(id, user_id, activity_id, activity_title, remark, expires_at)
            VALUES (#{id}, #{userId}, #{activityId}, #{title}, #{remark}, #{expiresAt})
            """)
    int insert(@Param("id") String id, @Param("userId") Long userId, @Param("activityId") long activityId,
               @Param("title") String title, @Param("remark") String remark, @Param("expiresAt") LocalDateTime expiresAt);

    @Select("""
            SELECT activity_id, activity_title AS title, remark, state, expires_at > NOW() AS current_flag
            FROM agent_action WHERE id = #{id} AND user_id = #{userId} FOR UPDATE
            """)
    Action lockAction(@Param("id") String id, @Param("userId") Long userId);

    @Update("UPDATE agent_action SET state='SUBMITTED', confirmed_at=NOW(), remark=#{remark} WHERE id=#{id}")
    int markSubmitted(@Param("id") String id, @Param("remark") String remark);

    @Select("""
            SELECT id, activity_id, activity_title, remark, expires_at,
                   CASE WHEN state='PENDING' AND expires_at<=NOW() THEN 'EXPIRED' ELSE state END AS result
            FROM agent_action WHERE id = #{id} AND user_id = #{userId}
            """)
    Card findCard(@Param("id") String id, @Param("userId") Long userId);

    record Action(long activityId, String title, String remark, String state, boolean currentFlag) {
        public boolean current() {
            return currentFlag;
        }
    }
}
