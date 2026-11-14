package com.campus.platform.module.ai.mapper;

import com.campus.platform.module.ai.agent.IdlePublishDrafts.Draft;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AgentIdleDraftMapper {
    String DRAFT_QUERY = """
            SELECT id, title, description, category, expect_item, expires_at, item_id,
                   CASE WHEN item_id IS NOT NULL THEN 'SUBMITTED'
                        WHEN expires_at <= NOW() THEN 'EXPIRED' ELSE 'DRAFT' END AS state
            FROM agent_idle_draft WHERE id = #{id} AND user_id = #{userId}
            """;

    @Select(DRAFT_QUERY)
    Draft findDraft(@Param("id") String id, @Param("userId") Long userId);

    @Select(DRAFT_QUERY + " FOR UPDATE")
    Draft lockDraft(@Param("id") String id, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO agent_idle_draft(id, user_id, title, description, category, expect_item, expires_at)
            VALUES (#{id}, #{userId}, #{title}, #{description}, #{category}, #{expectItem}, DATE_ADD(NOW(), INTERVAL 1 DAY))
            """)
    int insertDraft(@Param("id") String id, @Param("userId") Long userId, @Param("title") String title,
                    @Param("description") String description, @Param("category") String category,
                    @Param("expectItem") String expectItem);

    @Update("""
            UPDATE agent_idle_draft SET item_id=#{itemId}, title=#{title}, description=#{description},
                category=#{category}, expect_item=#{expectItem} WHERE id=#{id}
            """)
    int markSubmitted(@Param("id") String id, @Param("itemId") Long itemId, @Param("title") String title,
                      @Param("description") String description, @Param("category") String category,
                      @Param("expectItem") String expectItem);
}
