package com.campus.platform.module.drawgame.websocket.impl;

import com.campus.platform.module.drawgame.websocket.IDrawGuessWsTicketService;
import jakarta.annotation.Resource;
import com.campus.platform.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class DrawGuessWsTicketServiceImpl implements IDrawGuessWsTicketService {
    private static final String PREFIX = "draw-guess:ws-ticket:";
    private static final long TTL_SECONDS = 60;

    @Resource
    private RedisUtils redisUtils;

    public DrawGuessWsTicketServiceImpl(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }


    @Override
    public String issue(Long userId, Long roomId) {
        if (userId == null || userId <= 0 || roomId == null || roomId <= 0) {
            throw new IllegalArgumentException("用户和房间编号必须有效");
        }
        String ticket = UUID.randomUUID().toString();
        redisUtils.set(PREFIX + ticket, userId + ":" + roomId, TTL_SECONDS, TimeUnit.SECONDS);
        return ticket;
    }

    @Override
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
