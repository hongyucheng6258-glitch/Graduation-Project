package com.campus.platform.module.drawgame.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DrawGuessSessionRegistry {
    private static final Logger log = LoggerFactory.getLogger(DrawGuessSessionRegistry.class);
    public static final String ROOM_ID_ATTRIBUTE = "drawGuessRoomId";
    public static final String USER_ID_ATTRIBUTE = "drawGuessUserId";

    private final ObjectMapper objectMapper;
    private final ConcurrentMap<Long, ConcurrentMap<String, WebSocketSession>> sessionsByRoom =
            new ConcurrentHashMap<>();

    public void register(Long roomId, WebSocketSession session) {
        if (roomId == null || session == null) return;
        sessionsByRoom.compute(roomId, (ignored, roomSessions) -> {
            ConcurrentMap<String, WebSocketSession> target = roomSessions == null
                    ? new ConcurrentHashMap<>() : roomSessions;
            target.put(session.getId(), session);
            return target;
        });
    }

    public void unregister(WebSocketSession session) {
        if (session == null) return;
        Object roomValue = session.getAttributes().get(ROOM_ID_ATTRIBUTE);
        if (!(roomValue instanceof Number roomNumber)) return;
        sessionsByRoom.computeIfPresent(roomNumber.longValue(), (ignored, roomSessions) -> {
            roomSessions.remove(session.getId(), session);
            return roomSessions.isEmpty() ? null : roomSessions;
        });
    }

    public void broadcast(Long roomId, Object event) {
        if (roomId == null) return;
        send(sessions(roomId), event);
    }

    public void sendToUser(Long roomId, Long userId, Object event) {
        if (roomId == null || userId == null) return;
        List<WebSocketSession> recipients = sessions(roomId).stream()
                .filter(session -> userId.equals(userIdOf(session)))
                .toList();
        send(recipients, event);
    }

    public void sendToSession(WebSocketSession session, Object event) {
        if (session != null) send(List.of(session), event);
    }

    public Set<Long> onlineUserIds(Long roomId) {
        return sessions(roomId).stream().map(DrawGuessSessionRegistry::userIdOf)
                .filter(userId -> userId != null).collect(Collectors.toUnmodifiableSet());
    }

    public int onlineCount(Long roomId) {
        return onlineUserIds(roomId).size();
    }

    public void closeUserSessions(Long roomId, Long userId) {
        for (WebSocketSession session : sessions(roomId)) {
            if (!userId.equals(userIdOf(session))) continue;
            try {
                if (session.isOpen()) session.close(CloseStatus.NORMAL);
            } catch (IOException exception) {
                log.debug("退出你画我猜房间时关闭连接失败，sessionId={}", session.getId(), exception);
            }
        }
    }

    public List<WebSocketSession> sessions(Long roomId) {
        ConcurrentMap<String, WebSocketSession> roomSessions = sessionsByRoom.get(roomId);
        return roomSessions == null ? List.of() : List.copyOf(roomSessions.values());
    }

    private static Long userIdOf(WebSocketSession session) {
        Object userValue = session.getAttributes().get(USER_ID_ATTRIBUTE);
        return userValue instanceof Number number ? number.longValue() : null;
    }

    private void send(List<WebSocketSession> recipients, Object event) {
        if (recipients.isEmpty()) return;
        final String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            log.warn("你画我猜事件序列化失败", exception);
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : recipients) {
            if (!session.isOpen()) continue;
            try {
                synchronized (session) {
                    if (session.isOpen()) session.sendMessage(message);
                }
            } catch (IOException exception) {
                log.debug("你画我猜 WebSocket 推送失败，sessionId={}", session.getId(), exception);
            }
        }
    }
}
