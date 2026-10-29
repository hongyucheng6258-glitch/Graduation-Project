package com.campus.platform.module.message.mapper;

import com.campus.platform.module.message.entity.Message;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Message Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
    @Insert("INSERT INTO message(user_id, type, title, content, biz_type, biz_id, is_read) " +
            "VALUES(#{userId}, 'private_message', '收到新私信', #{content}, 'conversation', #{conversationId}, 0) " +
            "ON DUPLICATE KEY UPDATE content = VALUES(content), create_time = CURRENT_TIMESTAMP")
    int upsertPrivateMessage(@Param("userId") Long userId,
                             @Param("conversationId") Long conversationId,
                             @Param("content") String content);
}
