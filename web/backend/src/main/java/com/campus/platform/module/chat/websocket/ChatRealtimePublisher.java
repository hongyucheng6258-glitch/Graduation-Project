package com.campus.platform.module.chat.websocket;


import com.campus.platform.module.chat.entity.ChatMessage;
import com.campus.platform.module.chat.vo.ChatMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatRealtimePublisher {
    private final ChatSessionRegistry registry;

    public void messageAfterCommit(ChatMessage message, long receiverUnread) {
        runAfterCommit(() -> {
            Map<String, Object> messageEvent = Map.of(
                    "type", "chat.message",
                    "message", ChatMessageVO.from(message));
            registry.send(message.getReceiverId(), messageEvent);
            registry.send(message.getSenderId(), messageEvent);
            registry.send(message.getReceiverId(), Map.of(
                    "type", "chat.unread",
                    "conversationId", message.getConversationId(),
                    "unreadCount", receiverUnread,
                    "version", message.getId()));
        });
    }

    public void groupCreatedAfterCommit(Long conversationId, Iterable<Long> memberIds) {
        runAfterCommit(() -> {
            Map<String, Object> event = Map.of("type", "chat.conversation", "conversationId", conversationId);
            memberIds.forEach(id -> registry.send(id, event));
        });
    }

    public void groupMessageAfterCommit(ChatMessage message, Map<Long, Integer> unreadByMember) {
        runAfterCommit(() -> {
            Map<String, Object> event = Map.of("type", "chat.message", "message", ChatMessageVO.from(message));
            registry.send(message.getSenderId(), event);
            unreadByMember.forEach((memberId, unread) -> {
                registry.send(memberId, event);
                sendUnread(memberId, message.getConversationId(), message.getId(), unread);
            });
        });
    }

    public void groupReadAfterCommit(Long userId, Long conversationId, Long messageId, int unread) {
        runAfterCommit(() -> sendUnread(userId, conversationId, messageId, unread));
    }

    private void sendUnread(Long userId, Long conversationId, Long messageId, long unread) {
        registry.send(userId, Map.of("type", "chat.unread", "conversationId", conversationId,
                "unreadCount", unread, "version", messageId));
    }

    public void readAfterCommit(Long readerId, Long peerId, Long conversationId, Long lastReadMessageId,
                                long remainingUnread) {
        runAfterCommit(() -> {
            registry.send(readerId, Map.of(
                    "type", "chat.unread",
                    "conversationId", conversationId,
                    "unreadCount", remainingUnread,
                    "version", lastReadMessageId == null ? 0L : lastReadMessageId));
            registry.send(peerId, Map.of(
                    "type", "chat.read-receipt",
                    "conversationId", conversationId,
                    "readerId", readerId,
                    "lastReadMessageId", lastReadMessageId == null ? 0L : lastReadMessageId));
        });
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
