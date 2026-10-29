package com.campus.platform.module.chat.websocket;


import com.campus.platform.module.chat.entity.ChatMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ChatRealtimePublisherTest {

    @Test
    void newMessageIsSynchronizedToReceiverAndAllSenderDevices() {
        ChatSessionRegistry registry = mock(ChatSessionRegistry.class);
        ChatRealtimePublisher publisher = new ChatRealtimePublisher(registry);
        ChatMessage message = new ChatMessage();
        message.setId(9L);
        message.setConversationId(10L);
        message.setSenderId(7L);
        message.setReceiverId(8L);
        message.setClientMessageId("client-1");
        message.setMessageType("text");
        message.setContent("hello");

        publisher.messageAfterCommit(message, 3L);

        ArgumentCaptor<Object> senderPayload = ArgumentCaptor.forClass(Object.class);
        verify(registry).send(eq(7L), senderPayload.capture());
        assertEquals("chat.message", ((Map<?, ?>) senderPayload.getValue()).get("type"));

        ArgumentCaptor<Object> receiverPayloads = ArgumentCaptor.forClass(Object.class);
        verify(registry, times(2)).send(eq(8L), receiverPayloads.capture());
        assertEquals("chat.message", ((Map<?, ?>) receiverPayloads.getAllValues().get(0)).get("type"));
        assertEquals("chat.unread", ((Map<?, ?>) receiverPayloads.getAllValues().get(1)).get("type"));
    }
}
