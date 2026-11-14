package com.campus.platform.module.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AgentRunMapper {
    @Insert("""
            INSERT INTO agent_run(user_id, session_id, prompt_version, model_name, tools, elapsed_ms, status)
            VALUES (#{userId}, #{sessionId,jdbcType=BIGINT}, #{promptVersion}, #{model}, #{tools}, #{elapsedMs}, #{status})
            """)
    int insertRun(@Param("userId") Long userId, @Param("sessionId") Long sessionId,
                  @Param("promptVersion") long promptVersion, @Param("model") String model,
                  @Param("tools") String tools, @Param("elapsedMs") long elapsedMs, @Param("status") String status);

    @Select("""
            SELECT id,prompt_version,model_name,tools,elapsed_ms,status,created_at
            FROM agent_run ORDER BY id DESC LIMIT 100
            """)
    List<Map<String, Object>> findRecent();

    @Select("SELECT CURRENT_DATE")
    LocalDate currentDate();

    @Select("""
            SELECT tools, status, COUNT(*) AS count
            FROM agent_run WHERE created_at >= #{start} AND created_at < #{end}
            GROUP BY tools, status
            """)
    List<RunGroup> groupByTools(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    record RunGroup(String tools, String status, long count) {}
}
