package com.campus.platform.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.platform.module.chat.entity.ChatGroupInvite;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatGroupInviteMapper extends BaseMapper<ChatGroupInvite> {
    @Insert("""
            INSERT INTO chat_group_invite (conversation_id, invitee_id, inviter_id)
            VALUES (#{conversationId}, #{inviteeId}, #{inviterId})
            """)
    int add(Long conversationId, Long inviteeId, Long inviterId);
}
