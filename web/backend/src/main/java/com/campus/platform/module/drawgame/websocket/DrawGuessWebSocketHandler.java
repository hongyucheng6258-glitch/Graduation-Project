package com.campus.platform.module.drawgame.websocket;

import com.campus.platform.common.BizException;
import com.campus.platform.module.drawgame.service.DrawGuessRoomService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DrawGuessWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(DrawGuessWebSocketHandler.class);
    private static final int MAX_MESSAGE_BYTES = 8192;

    private final ObjectMapper objectMapper;
    private final DrawGuessRoomService roomService;
    private final DrawGuessSessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long roomId = attributeId(session, DrawGuessSessionRegistry.ROOM_ID_ATTRIBUTE);
        Long userId = attributeId(session, DrawGuessSessionRegistry.USER_ID_ATTRIBUTE);
        if (roomId == null || userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("票据无效"));
            return;
        }
        sessionRegistry.register(roomId, session);
        try {
            roomService.onConnected(roomId, userId);
        } catch (RuntimeException error) {
            sessionRegistry.unregister(session);
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("房间成员状态已失效"));
            throw error;
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long roomId = attributeId(session, DrawGuessSessionRegistry.ROOM_ID_ATTRIBUTE);
        Long userId = attributeId(session, DrawGuessSessionRegistry.USER_ID_ATTRIBUTE);
        if (roomId == null || userId == null) return;
        if (message.getPayloadLength() > MAX_MESSAGE_BYTES
                || message.getPayload().getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
            sessionRegistry.sendToUser(roomId, userId,
                    Map.of("type", "error", "message", "消息内容过长，请缩短后重试"));
            return;
        }
        try {
            JsonNode payload = objectMapper.readTree(message.getPayload());
            roomService.handleSocketMessage(roomId, userId, payload);
        } catch (BizException | IllegalArgumentException | IllegalStateException error) {
            sessionRegistry.sendToUser(roomId, userId,
                    Map.of("type", "error", "message", error.getMessage() == null ? "操作失败" : error.getMessage()));
        } catch (Exception error) {
            log.warn("你画我猜 WebSocket 消息处理失败, roomId={}, userId={}", roomId, userId, error);
            sessionRegistry.sendToUser(roomId, userId, Map.of("type", "error", "message", "操作失败，请重试"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long roomId = attributeId(session, DrawGuessSessionRegistry.ROOM_ID_ATTRIBUTE);
        sessionRegistry.unregister(session);
        if (roomId != null) roomService.onDisconnected(roomId);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        Long roomId = attributeId(session, DrawGuessSessionRegistry.ROOM_ID_ATTRIBUTE);
        sessionRegistry.unregister(session);
        if (roomId != null) roomService.onDisconnected(roomId);
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    private static Long attributeId(WebSocketSession session, String key) {
        Object value = session.getAttributes().get(key);
        return value instanceof Number number ? number.longValue() : null;
    }
}
