package com.campus.platform.module.chat.websocket;


import com.campus.platform.module.chat.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatWebSocketHandlerTest {

    @Test
    void malformedInboundFrameAndErrorWriteFailureStayInsideUnifiedBoundary() throws Exception {
        ChatService chatService = mock(ChatService.class);
        ChatSessionRegistry registry = mock(ChatSessionRegistry.class);
        ChatWebSocketHandler handler = new ChatWebSocketHandler(new ObjectMapper(), chatService, registry);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        when(session.isOpen()).thenReturn(true);
        doThrow(new IOException("broken socket")).when(session).sendMessage(any(TextMessage.class));

        assertDoesNotThrow(() -> handler.handleTextMessage(session, new TextMessage("not-json")));

        verify(session).close(any());
        verifyNoInteractions(chatService);
    }
}
