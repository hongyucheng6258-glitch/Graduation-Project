package com.campus.platform.module.ai.mapper;

import com.campus.platform.module.ai.agent.AgentPromptService.Snapshot;
import com.campus.platform.module.ai.agent.AgentPromptService.Version;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AgentPromptMapper {
    @Select("""
            SELECT p.id, p.content FROM agent_prompt_active a
            JOIN agent_prompt_version p ON p.id = a.version_id AND p.scene = a.scene
            WHERE a.scene = #{scene}
            """)
    Snapshot findActive(@Param("scene") String scene);

    @Select("""
            SELECT p.id, p.content, p.created_at, COALESCE(a.version_id = p.id, 0) AS active
            FROM agent_prompt_version p LEFT JOIN agent_prompt_active a ON a.scene = p.scene
            WHERE p.scene = #{scene} ORDER BY p.id DESC LIMIT 100
            """)
    List<Version> findVersions(@Param("scene") String scene);

    @Insert("""
            INSERT INTO agent_prompt_version(scene, content, created_by)
            VALUES (#{scene}, #{content}, #{adminId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertVersion(NewVersion version);

    @Delete("DELETE FROM agent_prompt_active WHERE scene = #{scene}")
    int clearActive(@Param("scene") String scene);

    @Insert("""
            INSERT INTO agent_prompt_active(scene, version_id)
            SELECT scene, id FROM agent_prompt_version WHERE id = #{id} AND scene = #{scene}
            ON DUPLICATE KEY UPDATE version_id = #{id}
            """)
    int setActive(@Param("scene") String scene, @Param("id") long id);

    @Getter
    @RequiredArgsConstructor
    class NewVersion {
        private final String scene;
        private final String content;
        private final long adminId;
        @Setter
        private Long id;
    }
}
