package com.campus.platform.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.platform.module.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    @Select("SELECT * FROM chat_message WHERE sender_id = #{senderId} AND client_message_id = #{clientMessageId} LIMIT 1")
    ChatMessage findBySenderAndClientMessageId(Long senderId, String clientMessageId);

    @Select("SELECT * FROM chat_message WHERE id = #{messageId} AND conversation_id = #{conversationId} LIMIT 1")
    ChatMessage findMessageInConversation(Long messageId, Long conversationId);

    @Select("SELECT MAX(id) FROM chat_message WHERE conversation_id = #{conversationId} AND receiver_id = #{receiverId} " +
            "AND id <= #{upperBound}")
    Long findLatestReceivedMessageId(Long conversationId, Long receiverId, Long upperBound);

    @Update("UPDATE chat_message SET status = 1, read_time = #{readTime} WHERE conversation_id = #{conversationId} " +
            "AND receiver_id = #{receiverId} AND status = 0 AND id <= #{lastReadMessageId}")
    int markReadUpTo(@Param("conversationId") Long conversationId,
                     @Param("receiverId") Long receiverId,
                     @Param("lastReadMessageId") Long lastReadMessageId,
                     @Param("readTime") LocalDateTime readTime);
}
