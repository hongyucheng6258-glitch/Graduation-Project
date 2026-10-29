package com.campus.platform.module.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_conversation")
public class ChatConversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long user1Id;
    private Long user2Id;
    private Long lastMessageId;
    private String lastMessageSummary;
    private LocalDateTime lastMessageTime;
    private String contextType;
    private Long contextId;
    private String contextTitle;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
