package com.campus.platform.module.chat.websocket;

import com.campus.platform.module.chat.entity.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 第13项 WebSocket 与扩展能力回归：
 *  - ChatSessionRegistry：每用户连接上限、下线注销、关闭会话清理、推送只发给在线会话
 *  - ChatRealtimePublisher：无事务/提交后事件推送（chat.message / chat.unread / chat.read-receipt）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("私信 WebSocket 注册/推送")
class ChatWebSocketTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ChatSessionRegistry registry;

    private WebSocketSession session(long id) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn("s" + id);
        when(s.isOpen()).thenReturn(true);
        Map<String, Object> attrs = new HashMap<>();
        when(s.getAttributes()).thenReturn(attrs);
        return s;
    }

    private String sentJson(WebSocketSession s) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(s).sendMessage(captor.capture());
        return captor.getValue().getPayload();
    }

    @Test
    @DisplayName("每用户最多 5 条连接，第 6 条被拒")
    void register_shouldEnforcePerUserCap() {
        for (int i = 1; i <= 5; i++) {
            assertThat(registry.register(1L, session(i))).isTrue();
        }
        assertThat(registry.register(1L, session(6))).isFalse();
    }

    @Test
    @DisplayName("注册时自动清理已关闭会话，释放名额")
    void register_shouldPruneClosedSessions() {
        java.util.List<WebSocketSession> list = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            WebSocketSession s = session(i);
            list.add(s);
            assertThat(registry.register(1L, s)).isTrue();
        }
        WebSocketSession stale = list.get(0);
        when(stale.isOpen()).thenReturn(false);
        assertThat(registry.register(1L, session(6))).isTrue();
    }

    @Test
    @DisplayName("注销后不再接收推送；推送序列化为 JSON 且只发给在线会话")
    void unregisterAndSend_shouldTargetOnlyOpenSessions() throws Exception {
        WebSocketSession a = session(1);
        WebSocketSession b = session(2);
        registry.register(7L, a);
        registry.register(7L, b);
        registry.unregister(b);

        when(objectMapper.writeValueAsString(Map.of("type", "chat.ping"))).thenReturn("{\"type\":\"chat.ping\"}");
        registry.send(7L, Map.of("type", "chat.ping"));

        assertThat(sentJson(a)).isEqualTo("{\"type\":\"chat.ping\"}");
        verify(b, never()).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
    }

    @Test
    @DisplayName("发布器无事务环境直接推送：消息事件与未读数事件")
    void publisher_shouldPushWithoutTx() throws Exception {
        ChatSessionRegistry realRegistry = new ChatSessionRegistry(new ObjectMapper());
        ChatRealtimePublisher publisher = new ChatRealtimePublisher(realRegistry);
        WebSocketSession receiver = session(1);
        WebSocketSession sender = session(2);
        realRegistry.register(10L, receiver);
        realRegistry.register(11L, sender);

        ChatMessage msg = new ChatMessage();
        msg.setId(100L);
        msg.setConversationId(1L);
        msg.setSenderId(11L);
        msg.setReceiverId(10L);

        publisher.messageAfterCommit(msg, 3L);

        // receiver 收到 消息事件 + 未读数事件，sender 收到消息事件
        ArgumentCaptor<TextMessage> rc = ArgumentCaptor.forClass(TextMessage.class);
        verify(receiver, org.mockito.Mockito.times(2)).sendMessage(rc.capture());
        String receiverPayload = rc.getAllValues().stream()
                .map(TextMessage::getPayload).collect(java.util.stream.Collectors.joining("|"));
        assertThat(receiverPayload).contains("chat.message").contains("chat.unread");
        verify(sender, org.mockito.Mockito.times(1)).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
    }

    @Test
    @DisplayName("已读回执只发给会话双方")
    void publisher_readReceipt_shouldReachBothParties() throws Exception {
        ChatSessionRegistry realRegistry = new ChatSessionRegistry(new ObjectMapper());
        ChatRealtimePublisher publisher = new ChatRealtimePublisher(realRegistry);
        WebSocketSession reader = session(1);
        WebSocketSession peer = session(2);
        realRegistry.register(20L, reader);
        realRegistry.register(21L, peer);

        publisher.readAfterCommit(20L, 21L, 5L, 99L, 2L);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(reader, org.mockito.Mockito.times(1)).sendMessage(captor.capture());
        verify(peer, org.mockito.Mockito.times(1)).sendMessage(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
        String joined = captor.getAllValues().stream()
                .map(TextMessage::getPayload).collect(java.util.stream.Collectors.joining("|"));
        assertThat(joined).contains("chat.unread").contains("chat.read-receipt");
    }
}
