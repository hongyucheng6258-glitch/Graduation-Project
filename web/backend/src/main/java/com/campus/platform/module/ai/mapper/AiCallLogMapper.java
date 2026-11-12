package com.campus.platform.module.ai.mapper;

import com.campus.platform.module.ai.entity.AiCallLog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.platform.common.DailyCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AiCallLog Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {
    @Select("""
            SELECT DATE(create_time) AS day, COUNT(*) AS total
            FROM ai_call_log
            WHERE create_time >= #{start} AND create_time < #{end}
            GROUP BY DATE(create_time)
            """)
    List<DailyCount> countByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
