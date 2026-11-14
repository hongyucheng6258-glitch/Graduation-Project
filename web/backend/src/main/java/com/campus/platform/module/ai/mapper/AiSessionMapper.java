package com.campus.platform.module.ai.mapper;

import com.campus.platform.module.ai.entity.AiSession;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

/**
 * AiSession Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface AiSessionMapper extends BaseMapper<AiSession> {
    @Select("SELECT * FROM ai_session WHERE id = #{id} FOR UPDATE")
    AiSession selectByIdForUpdate(@Param("id") Long id);
}
