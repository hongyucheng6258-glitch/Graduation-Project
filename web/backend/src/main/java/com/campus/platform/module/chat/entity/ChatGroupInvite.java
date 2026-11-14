package com.campus.platform.module.chat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("chat_group_invite")
public class ChatGroupInvite {
    private Long id;
    private Long conversationId;
    private Long inviteeId;
    private Long inviterId;
}
