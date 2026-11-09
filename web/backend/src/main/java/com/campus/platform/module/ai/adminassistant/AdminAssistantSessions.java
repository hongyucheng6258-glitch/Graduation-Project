package com.campus.platform.module.ai.adminassistant;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class AdminAssistantSessions {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    List<Session> list(Long adminId) {
        return jdbc.query("""
                SELECT id,title,updated_at FROM admin_assistant_session WHERE admin_id=?
                ORDER BY updated_at DESC,id DESC LIMIT 50
                """, (rs, row) -> new Session(rs.getString(1), rs.getString(2), rs.getObject(3, LocalDateTime.class)), adminId);
    }

    List<AdminAssistantService.Turn> history(Long adminId, String sessionId, int limit) {
        if (sessionId == null) return List.of();
        requireOwner(adminId, sessionId, false);
        var turns = jdbc.query("""
                SELECT t.body FROM admin_assistant_turn t JOIN admin_assistant_session s ON s.id=t.session_id
                WHERE t.session_id=? AND s.admin_id=? ORDER BY t.id DESC LIMIT ?
                """, (rs, row) -> readTurn(rs.getString(1)), sessionId, adminId, limit);
        Collections.reverse(turns);
        return turns;
    }

    @Transactional
    String save(Long adminId, String sessionId, AdminAssistantService.Turn turn) {
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
            String title = turn.question().codePoints().limit(32)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
            jdbc.update("INSERT INTO admin_assistant_session(id,admin_id,title) VALUES(?,?,?)", sessionId, adminId, title);
        } else {
            requireOwner(adminId, sessionId, true);
            jdbc.update("UPDATE admin_assistant_session SET updated_at=CURRENT_TIMESTAMP(6) WHERE id=?", sessionId);
        }
        jdbc.update("INSERT INTO admin_assistant_turn(session_id,body) VALUES(?,?)", sessionId, writeTurn(turn));
        return sessionId;
    }

    void requireOwner(Long adminId, String sessionId) {
        requireOwner(adminId, sessionId, false);
    }

    private void requireOwner(Long adminId, String sessionId, boolean lock) {
        if (jdbc.queryForList("SELECT id FROM admin_assistant_session WHERE id=? AND admin_id=?"
                + (lock ? " FOR UPDATE" : ""), String.class, sessionId, adminId).isEmpty()) {
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
