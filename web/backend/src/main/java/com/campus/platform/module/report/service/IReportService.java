package com.campus.platform.module.report.service;

import com.campus.platform.common.PageResult;
import com.campus.platform.module.report.dto.ReportDTO;
import com.campus.platform.module.report.entity.Report;

/**
 * 举报服务接口（学生发起侧）：校验举报目标存在后落库，等待管理端处置。
 */
public interface IReportService {

    /** 发起举报 */
    Report submit(Long userId, ReportDTO dto);

    /** 当前学生提交的举报记录 */
    PageResult<Report> myList(Long userId, int pageNum, int pageSize);
}
