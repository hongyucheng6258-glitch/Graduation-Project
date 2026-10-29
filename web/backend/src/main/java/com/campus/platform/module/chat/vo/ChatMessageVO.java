package com.campus.platform.module.chat.vo;

import com.campus.platform.module.chat.entity.ChatMessage;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageVO {
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

    public static ChatMessageVO from(ChatMessage source) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.id = source.getId();
        vo.conversationId = source.getConversationId();
        vo.senderId = source.getSenderId();
        vo.receiverId = source.getReceiverId();
        vo.clientMessageId = source.getClientMessageId();
        vo.messageType = source.getMessageType();
        vo.content = source.getContent();
        vo.status = source.getStatus();
        vo.readTime = source.getReadTime();
        vo.createTime = source.getCreateTime();
        return vo;
    }
}
