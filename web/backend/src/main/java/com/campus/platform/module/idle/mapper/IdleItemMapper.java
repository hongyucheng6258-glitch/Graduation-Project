package com.campus.platform.module.idle.mapper;

import com.campus.platform.module.idle.entity.IdleItem;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * IdleItem Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface IdleItemMapper extends BaseMapper<IdleItem> {
    @Select("SELECT id,user_id,title,audit_status,audit_reason,ai_risk_level FROM idle_item WHERE id=#{id} FOR UPDATE")
    IdleItem lockForAudit(@Param("id") Long id);

    @Update("UPDATE idle_item SET audit_status=#{status},audit_reason=#{reason,jdbcType=VARCHAR},audit_source=#{source} WHERE id=#{id}")
    int updateAuditDecision(@Param("id") Long id, @Param("status") int status,
                            @Param("reason") String reason, @Param("source") String source);

    @Update("""
            <script>
            UPDATE idle_item SET status=#{status} WHERE id=#{id}
            <if test="expectedStatus != null"> AND status=#{expectedStatus}</if>
            <if test="approvedOnly"> AND audit_status=1</if>
            </script>
            """)
    int updateAvailability(@Param("id") Long id, @Param("status") int status,
                           @Param("expectedStatus") Integer expectedStatus, @Param("approvedOnly") boolean approvedOnly);
}
