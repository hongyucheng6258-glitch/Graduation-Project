package com.campus.platform.module.chat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话注册表（R8 复查修复：用户维度原子化）。
 *
 * <p>单实例能力：每个用户最多 {@link #MAX_CONNECTIONS_PER_USER} 条在线连接；
 * register / unregister 全部在 {@link ConcurrentHashMap#compute} 的 per-bin 锁内完成，
 * 容量判断+添加、空集合删除+注册交错都不再有竞态。
 *
 * <p>多实例能力说明：本注册表为<b>单机内存态</b>。多实例部署时需要配合
 * ① 网关粘性会话（同一用户路由到同一实例）或
 * ② 集中式 Pub/Sub（如 Redis Pub/Sub）广播投递，本类仅负责单实例内的会话管理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSessionRegistry {
    private static final int MAX_CONNECTIONS_PER_USER = 5;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /**
     * 注册会话：容量检查 + 清理已关闭连接 + 添加在同一个 compute 临界区内完成，
     * 并发注册不会突破上限；返回 false 表示已达上限。
     */
    public boolean register(Long userId, WebSocketSession session) {
        boolean[] accepted = {false};
        sessions.compute(userId, (k, set) -> {
            if (set == null) {
                set = ConcurrentHashMap.newKeySet();
            }
            set.removeIf(s -> !s.isOpen());
            if (set.size() >= MAX_CONNECTIONS_PER_USER) {
                accepted[0] = false;
                return set;
            }
            session.getAttributes().put("chatUserId", userId);
            set.add(session);
            accepted[0] = true;
            return set;
        });
        return accepted[0];
    }

    /** 注销会话：移除 + 空集合删除与注册互斥（同一 computeIfPresent 临界区）。 */
    public void unregister(WebSocketSession session) {
        Object value = session.getAttributes().get("chatUserId");
        if (!(value instanceof Long userId)) {
            return;
        }
        sessions.computeIfPresent(userId, (k, set) -> {
            set.remove(session);
            // 空集合移除；若在临界区内新注册了连接，computeIfPresent 仍返回原 set，
            // 只有真的为空才置 null 删除
            return set.isEmpty() ? null : set;
        });
    }

    public void send(Long userId, Object payload) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) return;
        try {
            String json = objectMapper.writeValueAsString(payload);
            for (WebSocketSession session : userSessions) {
                if (!session.isOpen()) continue;
                try {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(json));
                    }
                } catch (IOException error) {
                    log.warn("私信 WebSocket 推送失败, userId={}, sessionId={}", userId, session.getId(), error);
                }
            }
        } catch (Exception error) {
            log.warn("私信 WebSocket 序列化失败, userId={}", userId, error);
        }
    }
}
