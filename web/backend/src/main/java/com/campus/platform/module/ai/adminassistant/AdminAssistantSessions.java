package com.campus.platform.module.ai.adminassistant;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.campus.platform.module.ai.mapper.AdminAssistantSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAssistantSessions {
    private final AdminAssistantSessionMapper sessions;
    private final ObjectMapper json;

    List<Session> list(Long adminId) {
        return sessions.findSessions(adminId);
    }

    List<AdminAssistantService.Turn> history(Long adminId, String sessionId, int limit) {
        if (sessionId == null) return List.of();
        requireOwner(adminId, sessionId, false);
        var turns = new java.util.ArrayList<>(sessions.findHistory(sessionId, adminId, limit)
                .stream().map(this::readTurn).toList());
        Collections.reverse(turns);
        return turns;
    }

    @Transactional
    String save(Long adminId, String sessionId, AdminAssistantService.Turn turn) {
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
            String title = turn.question().codePoints().limit(32)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
            sessions.insertSession(sessionId, adminId, title);
        } else {
            requireOwner(adminId, sessionId, true);
            sessions.touchSession(sessionId);
        }
        sessions.insertTurn(sessionId, writeTurn(turn));
        return sessionId;
    }

    void requireOwner(Long adminId, String sessionId) {
        requireOwner(adminId, sessionId, false);
    }

    private void requireOwner(Long adminId, String sessionId, boolean lock) {
        String ownedSession = lock ? sessions.lockOwnedSession(sessionId, adminId)
                : sessions.findOwnedSession(sessionId, adminId);
        if (ownedSession == null) {
            throw new BizException(ResultCode.NOT_FOUND, "未找到管理助手会话");
        }
    }

    private AdminAssistantService.Turn readTurn(String content) {
        try {
            return json.readValue(content, AdminAssistantService.Turn.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法读取管理助手会话", e);
        }
    }

    private String writeTurn(AdminAssistantService.Turn turn) {
        try {
            return json.writeValueAsString(turn);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法保存管理助手会话", e);
        }
    }

    public record Session(String id, String title, LocalDateTime updatedAt) {}
}
