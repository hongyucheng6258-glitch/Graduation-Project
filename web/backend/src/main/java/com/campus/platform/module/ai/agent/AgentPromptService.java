package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentPromptService {
    private static final Set<String> SCENES = Set.of("campus_agent", "knowledge_answer");
    private final JdbcTemplate jdbc;

    public Snapshot active(String scene, String defaultPrompt) {
        return jdbc.query("""
                SELECT p.id,p.content FROM agent_prompt_active a
                JOIN agent_prompt_version p ON p.id=a.version_id AND p.scene=a.scene
                WHERE a.scene=?
                """, (rs, row) -> new Snapshot(rs.getLong(1), rs.getString(2)), scene)
                .stream().findFirst().orElse(new Snapshot(0L, defaultPrompt));
    }

    public List<Version> versions(String scene) {
        validate(scene);
        return jdbc.query("""
                SELECT p.id,p.content,p.created_at,COALESCE(a.version_id=p.id,0)
                FROM agent_prompt_version p LEFT JOIN agent_prompt_active a ON a.scene=p.scene
                WHERE p.scene=? ORDER BY p.id DESC LIMIT 100
                """, (rs, row) -> new Version(rs.getLong(1), rs.getString(2), rs.getObject(3, LocalDateTime.class),
                        rs.getBoolean(4)), scene);
    }

    public long create(String scene, String content, long adminId) {
        validate(scene);
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO agent_prompt_version(scene,content,created_by) VALUES(?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, scene);
            statement.setString(2, content);
            statement.setLong(3, adminId);
            return statement;
        }, key);
        return java.util.Objects.requireNonNull(key.getKey()).longValue();
    }

    public void publish(String scene, long id) {
        validate(scene);
        if (id == 0) {
            jdbc.update("DELETE FROM agent_prompt_active WHERE scene=?", scene);
            return;
        }
        int changed = jdbc.update("""
                INSERT INTO agent_prompt_active(scene,version_id)
                SELECT scene,id FROM agent_prompt_version WHERE id=? AND scene=?
                ON DUPLICATE KEY UPDATE version_id=?
                """, id, scene, id);
        if (changed == 0 && active(scene, "").id() != id) {
            throw new BizException(ResultCode.NOT_FOUND, "未找到该提示词版本");
        }
    }

    private void validate(String scene) {
        if (!SCENES.contains(scene)) {
            throw new BizException(ResultCode.BAD_REQUEST, "未知的提示词场景");
        }
    }

    public record Snapshot(long id, String content) {}
    public record Version(long id, String content, LocalDateTime createdAt, boolean active) {}
}
