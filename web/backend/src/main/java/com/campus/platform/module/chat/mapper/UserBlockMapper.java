package com.campus.platform.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.platform.module.chat.entity.UserBlock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserBlockMapper extends BaseMapper<UserBlock> {
    @Select("SELECT * FROM user_block WHERE user_id = #{userId} AND blocked_user_id = #{blockedUserId} LIMIT 1")
    UserBlock findBlock(Long userId, Long blockedUserId);

    @Select("SELECT COUNT(1) FROM user_block WHERE (user_id = #{user1Id} AND blocked_user_id = #{user2Id}) OR (user_id = #{user2Id} AND blocked_user_id = #{user1Id})")
    long countEitherDirection(Long user1Id, Long user2Id);
}
