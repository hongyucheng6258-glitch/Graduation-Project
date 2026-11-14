package com.campus.platform.module.friend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.platform.module.friend.Friendship;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FriendshipMapper extends BaseMapper<Friendship> {
    @Select("SELECT * FROM friendship WHERE user1_id = LEAST(#{userId}, #{peerId}) AND user2_id = GREATEST(#{userId}, #{peerId})")
    Friendship find(Long userId, Long peerId);

    @Select("SELECT * FROM friendship WHERE user1_id = LEAST(#{userId}, #{peerId}) AND user2_id = GREATEST(#{userId}, #{peerId}) FOR UPDATE")
    Friendship lock(Long userId, Long peerId);

    @Insert("""
        INSERT INTO friendship(user1_id, user2_id, requester_id, greeting, status)
        VALUES(LEAST(#{userId}, #{peerId}), GREATEST(#{userId}, #{peerId}), #{userId}, #{greeting}, 'new')
        ON DUPLICATE KEY UPDATE id = id
        """)
    int insertIfAbsent(Long userId, Long peerId, String greeting);
}
