package com.campus.platform.module.ai.mapper;

import com.campus.platform.module.ai.adminassistant.AdminAssistantSessions.Session;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface AdminAssistantSessionMapper {
    String OWNED_SESSION = "SELECT id FROM admin_assistant_session WHERE id=#{sessionId} AND admin_id=#{adminId}";

    @Select("""
            SELECT id,title,updated_at FROM admin_assistant_session WHERE admin_id=#{adminId}
            ORDER BY updated_at DESC,id DESC LIMIT 50
            """)
    List<Session> findSessions(@Param("adminId") Long adminId);

    @Select("""
            SELECT t.body FROM admin_assistant_turn t JOIN admin_assistant_session s ON s.id=t.session_id
            WHERE t.session_id=#{sessionId} AND s.admin_id=#{adminId} ORDER BY t.id DESC LIMIT #{limit}
            """)
    List<String> findHistory(@Param("sessionId") String sessionId, @Param("adminId") Long adminId,
                             @Param("limit") int limit);

    @Insert("INSERT INTO admin_assistant_session(id,admin_id,title) VALUES(#{id},#{adminId},#{title})")
    int insertSession(@Param("id") String id, @Param("adminId") Long adminId, @Param("title") String title);

    @Update("UPDATE admin_assistant_session SET updated_at=CURRENT_TIMESTAMP(6) WHERE id=#{id}")
    int touchSession(@Param("id") String id);

    @Insert("INSERT INTO admin_assistant_turn(session_id,body) VALUES(#{sessionId},#{body})")
    int insertTurn(@Param("sessionId") String sessionId, @Param("body") String body);

    @Select(OWNED_SESSION)
    String findOwnedSession(@Param("sessionId") String sessionId, @Param("adminId") Long adminId);

    @Select(OWNED_SESSION + " FOR UPDATE")
    String lockOwnedSession(@Param("sessionId") String sessionId, @Param("adminId") Long adminId);
}
