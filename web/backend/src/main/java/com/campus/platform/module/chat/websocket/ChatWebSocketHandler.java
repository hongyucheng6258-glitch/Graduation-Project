package com.campus.platform.module.chat.websocket;


import com.campus.platform.module.chat.dto.ChatSendDTO;
import com.campus.platform.module.chat.entity.ChatMessage;
import com.campus.platform.module.chat.service.ChatService;
import com.campus.platform.module.chat.vo.ChatMessageVO;
import com.campus.platform.common.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final ChatSessionRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("chatUserId");
        if (userId == null || !registry.register(userId, session)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("连接数超过限制"));
            return;
        }
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "chat.connected"))));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        Long userId = (Long) session.getAttributes().get("chatUserId");
        String requestId = null;
        try {
            JsonNode payload = objectMapper.readTree(textMessage.getPayload());
            if (payload == null || !payload.isObject()) throw new IllegalArgumentException("消息格式错误");
            // Web 与小程序客户端历史上使用 action，后端早期实现使用 type；兼容两者并统一按事件名处理。
            String type = payload.path("type").asText();
            if (type.isBlank()) type = payload.path("action").asText();
            requestId = payload.path("requestId").asText(null);
            switch (type) {
                case "ping" -> send(session, Map.of("type", "pong"));
                case "chat.send", "message.send" -> {
                    ChatSendDTO dto = new ChatSendDTO();
                    dto.setClientMessageId(requiredText(payload, "clientMessageId"));
                    dto.setMessageType(requiredText(payload, "messageType"));
                    dto.setContent(requiredText(payload, "content"));
                    ChatMessage message = chatService.sendMessage(userId, requiredLong(payload, "conversationId"), dto);
                    send(session, Map.of("type", "chat.ack", "requestId", safe(requestId),
                            "message", ChatMessageVO.from(message)));
                }
                case "chat.read", "conversation.read" -> {
                    Long conversationId = requiredLong(payload, "conversationId");
                    Long lastReadMessageId = payload.hasNonNull("lastReadMessageId") ? payload.get("lastReadMessageId").asLong() : null;
                    chatService.markRead(userId, conversationId, lastReadMessageId);
                }
                default -> sendError(session, requestId, "不支持的事件类型");
            }
        } catch (BizException | IllegalArgumentException error) {
            handleInboundFailure(session, requestId, error.getMessage(), error);
        } catch (Exception error) {
            handleInboundFailure(session, requestId, "服务器处理消息失败", error);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        registry.unregister(session);
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    private void handleInboundFailure(WebSocketSession session, String requestId, String message, Exception cause) {
        log.warn("私信 WebSocket 入站处理失败, sessionId={}, requestId={}", session.getId(), requestId, cause);
        try {
            sendError(session, requestId, message);
        } catch (Exception writeError) {
            log.warn("私信 WebSocket 错误响应发送失败, sessionId={}", session.getId(), writeError);
            registry.unregister(session);
            try {
                if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
            } catch (Exception closeError) {
                log.debug("私信 WebSocket 异常会话关闭失败, sessionId={}", session.getId(), closeError);
            }
        }
    }

    private void sendError(WebSocketSession session, String requestId, String message) throws Exception {
        send(session, Map.of("type", "chat.error", "requestId", safe(requestId), "message", message));
    }

    private void send(WebSocketSession session, Object payload) throws Exception {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
        return value;
    }

    private Long requiredLong(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.get(field).canConvertToLong()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return node.get(field).asLong();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
