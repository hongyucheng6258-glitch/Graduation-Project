package com.campus.platform.module.drawgame.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DrawGuessSessionRegistryTest {
    private DrawGuessSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DrawGuessSessionRegistry(new ObjectMapper());
    }

    @Test
    void broadcastsOnlyInsideTheTargetRoomAndCountsUsersOnceAcrossTabs() throws Exception {
        WebSocketSession firstTab = session("first", 10L, 101L);
        WebSocketSession secondTab = session("second", 10L, 101L);
        WebSocketSession otherRoom = session("other", 20L, 202L);
        registry.register(10L, firstTab);
        registry.register(10L, secondTab);
        registry.register(20L, otherRoom);

        assertEquals(1, registry.onlineCount(10L));
        assertEquals(1, registry.onlineCount(20L));
        registry.broadcast(10L, Map.of("type", "room_state"));

        verify(firstTab).sendMessage(any(TextMessage.class));
        verify(secondTab).sendMessage(any(TextMessage.class));
        verify(otherRoom, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void unregisterRemovesOnlyTheClosedSession() {
        WebSocketSession firstTab = session("first", 10L, 101L);
        WebSocketSession secondTab = session("second", 10L, 101L);
        registry.register(10L, firstTab);
        registry.register(10L, secondTab);

        registry.unregister(firstTab);

        assertEquals(1, registry.onlineCount(10L));
    }

    @Test
    void reconnectingSessionIsNotRemovedWithThePreviousEmptyRoomBucket() throws Exception {
        WebSocketSession disconnected = session("old", 10L, 101L);
        WebSocketSession reconnected = session("new", 10L, 101L);
        BlockingEmptyCheckMap bucket = new BlockingEmptyCheckMap();
        bucket.put(disconnected.getId(), disconnected);
        ConcurrentMap<Long, ConcurrentMap<String, WebSocketSession>> rooms = new ConcurrentHashMap<>();
        rooms.put(10L, bucket);
        Field sessionsField = DrawGuessSessionRegistry.class.getDeclaredField("sessionsByRoom");
        sessionsField.setAccessible(true);
        sessionsField.set(registry, rooms);

        Thread unregister = new Thread(() -> registry.unregister(disconnected));
        unregister.start();
        assertTrue(bucket.emptyObserved.await(1, TimeUnit.SECONDS));

        CountDownLatch registerStarted = new CountDownLatch(1);
        CountDownLatch registerFinished = new CountDownLatch(1);
        Thread register = new Thread(() -> {
            registerStarted.countDown();
            registry.register(10L, reconnected);
            registerFinished.countDown();
        });
        register.start();
        assertTrue(registerStarted.await(1, TimeUnit.SECONDS));
        // Let the non-atomic implementation publish into the old bucket before it removes that bucket.
        registerFinished.await(150, TimeUnit.MILLISECONDS);
        bucket.allowEmptyCheck.countDown();

        unregister.join(1_000);
        register.join(1_000);
        assertFalse(unregister.isAlive());
        assertFalse(register.isAlive());
        assertTrue(registry.sessions(10L).contains(reconnected));
    }

    private WebSocketSession session(String sessionId, Long roomId, Long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getAttributes()).thenReturn(Map.of(
                DrawGuessSessionRegistry.ROOM_ID_ATTRIBUTE, roomId,
                DrawGuessSessionRegistry.USER_ID_ATTRIBUTE, userId));
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static final class BlockingEmptyCheckMap extends ConcurrentHashMap<String, WebSocketSession> {
        private final CountDownLatch emptyObserved = new CountDownLatch(1);
        private final CountDownLatch allowEmptyCheck = new CountDownLatch(1);

        @Override
        public boolean isEmpty() {
            boolean empty = super.isEmpty();
            if (empty && emptyObserved.getCount() > 0) {
                emptyObserved.countDown();
                try {
                    if (!allowEmptyCheck.await(2, TimeUnit.SECONDS)) {
                        throw new AssertionError("test did not release the blocked empty check");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("empty check was interrupted", exception);
                }
            }
            return empty;
        }
    }
}
