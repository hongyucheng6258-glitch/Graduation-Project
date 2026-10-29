package com.campus.platform.module.chat.websocket;

import com.campus.platform.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ChatWsTicketService {
    private static final String PREFIX = "chat:ws-ticket:";
    private final RedisUtils redisUtils;

    public String issue(Long userId) {
        String ticket = UUID.randomUUID().toString();
        redisUtils.set(PREFIX + ticket, userId, 60, TimeUnit.SECONDS);
        return ticket;
    }

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
