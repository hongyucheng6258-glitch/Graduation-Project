package com.campus.platform.module.notice.mapper;

import com.campus.platform.module.notice.entity.Notice;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Notice Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {
}
