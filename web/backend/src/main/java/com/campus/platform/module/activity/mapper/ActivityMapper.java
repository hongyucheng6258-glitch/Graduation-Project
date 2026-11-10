package com.campus.platform.module.activity.mapper;

import com.campus.platform.module.activity.entity.Activity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Update;

/**
 * Activity Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
    @Select("SELECT * FROM activity WHERE id = #{id} FOR UPDATE")
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Activity selectForUpdate(@Param("id") Long id);

    @Update("UPDATE activity SET audit_status=#{status},audit_reason=#{reason,jdbcType=VARCHAR},audit_source=#{source} WHERE id=#{id}")
    int updateAuditDecision(@Param("id") Long id, @Param("status") int status,
                            @Param("reason") String reason, @Param("source") String source);

    @Update("UPDATE activity SET audit_revision=#{revision} WHERE id=#{id}")
    void updateAuditRevision(@Param("id") Long id, @Param("revision") String revision);

    @Update("UPDATE activity SET status=#{status} WHERE id=#{id}")
    void updateStatus(@Param("id") Long id, @Param("status") int status);

    @Update("UPDATE activity SET status=#{status} WHERE id=#{id} AND status=#{expected}")
    void replaceStatus(@Param("id") Long id, @Param("expected") int expected, @Param("status") int status);
}
