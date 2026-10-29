package com.campus.platform.module.admin.mapper;

import com.campus.platform.module.admin.entity.Admin;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Admin Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface AdminMapper extends BaseMapper<Admin> {
}
