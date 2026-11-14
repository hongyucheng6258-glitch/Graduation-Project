package com.campus.platform.module.idle.mapper;

import com.campus.platform.module.idle.entity.IdleItem;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.campus.platform.common.Constants;
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
            UPDATE idle_item SET status=#{target}
            WHERE id=#{id} AND status=#{source}
              AND (#{approvedOnly}=FALSE OR audit_status=1)
              AND NOT EXISTS (SELECT 1 FROM idle_appointment a
                              WHERE a.item_id=#{id} AND a.status IN (0,1,3))
            """)
    int changeListing(@Param("id") Long id, @Param("source") int source,
                      @Param("target") int target, @Param("approvedOnly") boolean approvedOnly);

    @Update("""
            UPDATE idle_item SET status=0 WHERE id=#{id} AND status=1
              AND NOT EXISTS (SELECT 1 FROM idle_appointment a
                              WHERE a.item_id=#{id} AND a.status IN (0,1,3))
            """)
    int releaseReservation(@Param("id") Long id);

    @Update("""
            <script>
            UPDATE idle_item SET status=#{status} WHERE id=#{id}
            <if test="expectedStatus != null"> AND status=#{expectedStatus}</if>
            <if test="approvedOnly"> AND audit_status=1</if>
            </script>
            """)
    int updateAvailability(@Param("id") Long id, @Param("status") int status,
                           @Param("expectedStatus") Integer expectedStatus, @Param("approvedOnly") boolean approvedOnly);
    @Update("UPDATE idle_item SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);
    default int applyAiAudit(IdleItem item, String idleRevision, String imageHash, int riskLevel,
                             String reason, java.time.LocalDateTime now, boolean autoPass) {
        return update(null, new UpdateWrapper<IdleItem>()
                        .eq("id", item.getId()).eq("audit_status", Constants.AUDIT_PENDING)
                        .eq(idleRevision != null, "audit_revision", idleRevision)
                        .apply("BINARY title <=> BINARY {0}", item.getTitle())
                        .apply("BINARY description <=> BINARY {0}", item.getDescription())
                        .apply("BINARY category <=> BINARY {0}", item.getCategory())
                        .apply("BINARY expect_item <=> BINARY {0}", item.getExpectItem())
                        .apply("SHA2(images,256) <=> {0}", imageHash)
                        .apply(idleRevision == null, "ai_audit_time <=> {0}", item.getAiAuditTime())
                        .apply(idleRevision == null, "ai_risk_level <=> {0}", item.getAiRiskLevel())
                        .apply(idleRevision == null, "BINARY ai_audit_reason <=> BINARY {0}", item.getAiAuditReason())
                        .set("ai_risk_level", riskLevel).set("ai_audit_reason", reason)
                        .set("ai_audit_time", now).set("audit_source", "ai")
                        .set(autoPass, "audit_status", Constants.AUDIT_PASS));
    }
}
