package com.campus.platform.module.favorite.mapper;

import com.campus.platform.module.favorite.entity.Favorite;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Favorite Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
