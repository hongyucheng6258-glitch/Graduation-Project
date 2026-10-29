package com.campus.platform.module.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long senderId;
    private Long receiverId;
    private String clientMessageId;
    private String messageType;
    private String content;
    private Integer status;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
}
