package com.campus.platform.module.admin.vo;

import lombok.Data;

/**
 * 数据大屏数字卡片（D7）。
 */
@Data
public class StatsOverviewVO {

    /** 总用户数 */
    private Long totalUsers;

    /** 今日活跃（当日登录 + AI调用 + 发布行为并集去重，共享约定 #11） */
    private Long todayActiveUsers;

    /** 今日AI调用次数 */
    private Long todayAiCalls;

    /** 待审核内容数 */
    private Long pendingAudits;
}
