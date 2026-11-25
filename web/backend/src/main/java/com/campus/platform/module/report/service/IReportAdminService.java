package com.campus.platform.module.report.service;

import com.campus.platform.common.PageResult;
import com.campus.platform.module.report.dto.ReportHandleDTO;
import com.campus.platform.module.report.entity.Report;

/**
 * 管理端-举报处置闭环服务接口。
 */
public interface IReportAdminService {

    /** 聊天证据（举报对象为聊天消息时返回原消息摘要） */
    record ChatEvidence(Long senderId, String messageType, String text) {}

    /** 举报列表（状态筛选） */
    PageResult<Report> list(Integer status, Long record, int pageNum, int pageSize);

    /** 举报详情 */
    Report detail(Long id);

    /** 聊天证据 */
    ChatEvidence chatEvidence(Long id);

    /**
     * 处置举报。
     * action: offline=下架内容 / warn=警告发布者 / ban=封禁发布者 / ignore=举报不成立
     */
    void handle(Long adminId, Long reportId, ReportHandleDTO dto);
}
