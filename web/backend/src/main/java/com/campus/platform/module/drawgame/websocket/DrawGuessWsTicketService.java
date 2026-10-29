package com.campus.platform.module.drawgame.websocket;

import com.campus.platform.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DrawGuessWsTicketService {
    private static final String PREFIX = "draw-guess:ws-ticket:";
    private static final long TTL_SECONDS = 60;

    private final RedisUtils redisUtils;

    public record TicketClaims(Long userId, Long roomId) { }

    public String issue(Long userId, Long roomId) {
        if (userId == null || userId <= 0 || roomId == null || roomId <= 0) {
            throw new IllegalArgumentException("用户和房间编号必须有效");
        }
        String ticket = UUID.randomUUID().toString();
        redisUtils.set(PREFIX + ticket, userId + ":" + roomId, TTL_SECONDS, TimeUnit.SECONDS);
        return ticket;
    }

    public TicketClaims consume(String ticket) {
        if (ticket == null || ticket.isBlank()) return null;
        Object value = redisUtils.getAndDelete(PREFIX + ticket);
        if (value == null) return null;
        String[] parts = value.toString().split(":", -1);
        if (parts.length != 2) return null;
        try {
            long userId = Long.parseLong(parts[0]);
            long roomId = Long.parseLong(parts[1]);
            return userId > 0 && roomId > 0 ? new TicketClaims(userId, roomId) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
