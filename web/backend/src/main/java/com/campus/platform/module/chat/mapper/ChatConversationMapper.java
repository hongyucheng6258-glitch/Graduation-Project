package com.campus.platform.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.platform.module.chat.entity.ChatConversation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
    @Select("SELECT * FROM chat_conversation WHERE user1_id = LEAST(#{user1Id}, #{user2Id}) AND user2_id = GREATEST(#{user1Id}, #{user2Id}) LIMIT 1")
    ChatConversation findByUserPair(Long user1Id, Long user2Id);

    @Select("SELECT * FROM chat_conversation WHERE id = #{conversationId} LIMIT 1 FOR UPDATE")
    ChatConversation findByIdForUpdate(Long conversationId);

    @Insert("INSERT INTO chat_conversation(user1_id, user2_id, context_type, context_id, context_title) " +
            "VALUES(#{user1Id}, #{user2Id}, #{contextType}, #{contextId}, #{contextTitle}) " +
            "ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), context_type = VALUES(context_type), " +
            "context_id = VALUES(context_id), context_title = VALUES(context_title)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertConversation(ChatConversation conversation);
}
