package com.campus.platform.module.ai.mapper;

import com.campus.platform.module.ai.adminassistant.AdminAssistantNoticeDrafts.Draft;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface AdminAssistantNoticeDraftMapper {
    String DRAFT_QUERY = """
            SELECT id,title,content,expires_at,CAST(notice_id AS CHAR) AS notice_id,
                CASE WHEN notice_id IS NOT NULL THEN 'SAVED' WHEN expires_at<=NOW() THEN 'EXPIRED' ELSE 'DRAFT' END AS state
            FROM admin_assistant_notice_draft
            """;
    String OWNED_DRAFT = DRAFT_QUERY + " WHERE id=#{id} AND admin_id=#{adminId}";

    @Select(DRAFT_QUERY + " WHERE admin_id=#{adminId} ORDER BY created_at DESC,id DESC LIMIT 30")
    List<Draft> findRecent(@Param("adminId") Long adminId);

    @Select(OWNED_DRAFT)
    Draft findDraft(@Param("id") String id, @Param("adminId") Long adminId);

    @Select(OWNED_DRAFT + " FOR UPDATE")
    Draft lockDraft(@Param("id") String id, @Param("adminId") Long adminId);

    @Insert("""
            INSERT INTO admin_assistant_notice_draft(id,admin_id,title,content,expires_at)
            VALUES(#{id},#{adminId},#{title},#{content},DATE_ADD(NOW(),INTERVAL 1 DAY))
            """)
    int insertDraft(@Param("id") String id, @Param("adminId") Long adminId,
                    @Param("title") String title, @Param("content") String content);

    @Update("UPDATE admin_assistant_notice_draft SET notice_id=#{noticeId},title=#{title},content=#{content} WHERE id=#{id}")
    int markSaved(@Param("id") String id, @Param("noticeId") Long noticeId,
                  @Param("title") String title, @Param("content") String content);
}
