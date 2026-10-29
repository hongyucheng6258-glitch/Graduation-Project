package com.campus.platform.module.report.mapper;

import com.campus.platform.module.report.entity.Report;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Report Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {
}
