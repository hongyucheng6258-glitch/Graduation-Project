package com.campus.platform.module.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_conversation_member")
public class ChatConversationMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long userId;
    private Integer unreadCount;
    private Long lastReadMessageId;
    private LocalDateTime readTime;
    private Integer muted;
    private Integer hidden;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
