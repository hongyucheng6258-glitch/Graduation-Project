package com.campus.platform.module.notice.service;

import com.campus.platform.common.PageResult;
import com.campus.platform.module.notice.entity.Notice;
import com.campus.platform.module.site.vo.HomeAggregateVO;

/**
 * 公告与首页聚合服务接口。
 */
public interface INoticeService {

    /** 公告列表（公开，仅已发布） */
    PageResult<Notice> list(int pageNum, int pageSize);

    /** 公告详情（公开，仅已发布） */
    Notice detail(Long id);

    /** 首页聚合（公告轮播 + 各模块最新3条） */
    HomeAggregateVO homeAggregate();
}
