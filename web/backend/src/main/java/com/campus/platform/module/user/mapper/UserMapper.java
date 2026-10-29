package com.campus.platform.module.user.mapper;

import com.campus.platform.module.user.entity.User;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * User Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
