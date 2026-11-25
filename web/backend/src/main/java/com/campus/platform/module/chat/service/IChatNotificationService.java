package com.campus.platform.module.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.platform.module.message.entity.Message;
import com.campus.platform.module.message.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ChatNotificationService 服务接口。
 */
public interface IChatNotificationService {

    void remind(Long receiverId, Long conversationId, String senderName);

    void clear(Long userId, Long conversationId);
}
