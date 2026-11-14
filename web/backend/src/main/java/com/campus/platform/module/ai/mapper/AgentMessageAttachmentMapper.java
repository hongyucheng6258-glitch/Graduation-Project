package com.campus.platform.module.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AgentMessageAttachmentMapper {
    @Insert("INSERT INTO agent_message_attachment(message_id, body) VALUES (#{messageId}, #{body})")
    int insertAttachment(@Param("messageId") long messageId, @Param("body") String body);

    @Select("SELECT body FROM agent_message_attachment WHERE message_id = #{messageId}")
    String findBody(@Param("messageId") Long messageId);
}
