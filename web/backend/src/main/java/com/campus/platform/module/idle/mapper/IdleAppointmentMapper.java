package com.campus.platform.module.idle.mapper;

import com.campus.platform.module.idle.entity.IdleAppointment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

/**
 * IdleAppointment Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface IdleAppointmentMapper extends BaseMapper<IdleAppointment> {
    @Select("SELECT * FROM idle_appointment WHERE id=#{id} FOR UPDATE")
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    IdleAppointment selectForUpdate(long id);
}
