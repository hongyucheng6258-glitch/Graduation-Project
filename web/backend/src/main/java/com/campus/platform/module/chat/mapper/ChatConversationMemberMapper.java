package com.campus.platform.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.platform.module.chat.entity.ChatConversationMember;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ChatConversationMemberMapper extends BaseMapper<ChatConversationMember> {
    @Select("SELECT * FROM chat_conversation_member WHERE conversation_id = #{conversationId} AND user_id = #{userId} LIMIT 1")
    ChatConversationMember findMember(Long conversationId, Long userId);

    @Select("SELECT * FROM chat_conversation_member WHERE conversation_id = #{conversationId} AND user_id = #{userId} LIMIT 1 FOR UPDATE")
    ChatConversationMember findMemberForUpdate(Long conversationId, Long userId);

    @Insert("INSERT IGNORE INTO chat_conversation_member(conversation_id, user_id, unread_count, muted, hidden) " +
            "VALUES(#{conversationId}, #{userId}, 0, 0, 0)")
    int insertMemberIfAbsent(Long conversationId, Long userId);

    @Update("UPDATE chat_conversation_member SET hidden = 0 WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
    int restoreMember(Long conversationId, Long userId);

    @Update("UPDATE chat_conversation_member SET unread_count = unread_count + 1, hidden = 0 " +
            "WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
    int incrementUnreadAndRestore(Long conversationId, Long userId);

    @Update("UPDATE chat_conversation_member SET " +
            "unread_count = GREATEST(0, unread_count - (" +
            "SELECT COUNT(*) FROM chat_message WHERE conversation_id = #{conversationId} " +
            "AND receiver_id = #{userId} AND status = 0 AND id <= #{lastReadMessageId})), " +
            "last_read_message_id = #{lastReadMessageId}, read_time = #{readTime} " +
            "WHERE conversation_id = #{conversationId} AND user_id = #{userId} " +
            "AND (last_read_message_id IS NULL OR last_read_message_id < #{lastReadMessageId})")
    int markReadMonotonic(@Param("conversationId") Long conversationId,
                          @Param("userId") Long userId,
                          @Param("lastReadMessageId") Long lastReadMessageId,
                          @Param("readTime") LocalDateTime readTime);
}
