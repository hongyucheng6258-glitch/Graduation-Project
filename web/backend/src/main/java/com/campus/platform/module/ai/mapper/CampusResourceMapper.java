package com.campus.platform.module.ai.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CampusResourceMapper {
    String PAGE_OR_IDS = """
            <choose>
                <when test="ids != null">
                    AND id IN
                    <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
                </when>
                <otherwise>ORDER BY id DESC LIMIT 6</otherwise>
            </choose>
            </script>
            """;

    @Select("""
            <script>
            SELECT id,title,location AS subtitle,type AS status,NULL AS profile_id FROM lost_found
            WHERE audit_status=#{approved} AND status=#{active}
            <if test="term != null">
                AND (title LIKE #{term} ESCAPE '!' OR description LIKE #{term} ESCAPE '!' OR location LIKE #{term} ESCAPE '!')
            </if>
            <if test="lostType != null">AND type=#{lostType}</if>
            """ + PAGE_OR_IDS)
    List<ResourceRow> findLost(@Param("approved") int approved, @Param("active") int active,
                               @Param("term") String term, @Param("lostType") Long lostType, @Param("ids") List<Long> ids);

    @Select("""
            <script>
            SELECT id,subject AS title,goal AS subtitle,status,user_id AS profile_id FROM study_partner
            WHERE audit_status=#{approved} AND status=#{active}
            <if test="term != null">
                AND (subject LIKE #{term} ESCAPE '!' OR goal LIKE #{term} ESCAPE '!' OR intro LIKE #{term} ESCAPE '!')
            </if>
            """ + PAGE_OR_IDS)
    List<ResourceRow> findPartners(@Param("approved") int approved, @Param("active") int active,
                                   @Param("term") String term, @Param("ids") List<Long> ids);

    @Select("""
            <script>
            SELECT id,title,category AS subtitle,status,NULL AS profile_id FROM campus_question
            WHERE status &lt;&gt; #{off}
            <if test="term != null">AND (title LIKE #{term} ESCAPE '!' OR content LIKE #{term} ESCAPE '!')</if>
            """ + PAGE_OR_IDS)
    List<ResourceRow> findQuestions(@Param("off") int off, @Param("term") String term, @Param("ids") List<Long> ids);

    record ResourceRow(long id, String title, String subtitle, int status, Long profileId) {}
}
