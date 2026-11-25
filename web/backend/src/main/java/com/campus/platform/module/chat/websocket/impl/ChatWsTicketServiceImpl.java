package com.campus.platform.module.chat.websocket.impl;

import com.campus.platform.module.chat.websocket.IChatWsTicketService;
import jakarta.annotation.Resource;
import com.campus.platform.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ChatWsTicketServiceImpl implements IChatWsTicketService {
    private static final String PREFIX = "chat:ws-ticket:";
    @Resource
    private RedisUtils redisUtils;

    public ChatWsTicketServiceImpl(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

    @Override
    public String issue(Long userId) {
        String ticket = UUID.randomUUID().toString();
        redisUtils.set(PREFIX + ticket, userId, 60, TimeUnit.SECONDS);
        return ticket;
    }

    @Override
    public Long consume(String ticket) {
        if (ticket == null || ticket.isBlank()) return null;
        Object value = redisUtils.getAndDelete(PREFIX + ticket);
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
