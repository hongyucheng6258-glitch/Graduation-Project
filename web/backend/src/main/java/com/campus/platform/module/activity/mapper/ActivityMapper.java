package com.campus.platform.module.activity.mapper;

import com.campus.platform.module.activity.entity.Activity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Options;

/**
 * Activity Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
    @Select("SELECT * FROM activity WHERE id = #{id} FOR UPDATE")
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Activity selectForUpdate(@Param("id") Long id);
}
