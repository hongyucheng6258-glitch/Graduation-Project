package com.campus.platform.module.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.platform.module.message.entity.Message;
import com.campus.platform.module.message.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatNotificationService {
    private final MessageMapper messageMapper;

    public void remind(Long receiverId, Long conversationId, String senderName) {
        String content = (senderName == null || senderName.isBlank() ? "有用户" : senderName) + "给你发来新私信";
        messageMapper.upsertPrivateMessage(receiverId, conversationId, content);
    }

    public void clear(Long userId, Long conversationId) {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getType, "private_message")
                .eq(Message::getBizType, "conversation")
                .eq(Message::getBizId, conversationId)
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1));
    }
}
