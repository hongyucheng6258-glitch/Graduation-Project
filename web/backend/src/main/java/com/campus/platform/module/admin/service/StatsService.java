package com.campus.platform.module.admin.service;

import com.campus.platform.module.admin.vo.StatsOverviewVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.common.Constants;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.ai.entity.AiCallLog;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.post.entity.Post;
import com.campus.platform.module.report.entity.Report;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.ai.mapper.AiCallLogMapper;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.post.mapper.PostMapper;
import com.campus.platform.module.report.mapper.ReportMapper;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.module.partner.entity.StudyPartner;
import com.campus.platform.module.partner.mapper.StudyPartnerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据大屏统计服务（D7）。
 * "今日活跃"口径（共享约定 #11）：当日登录 + AI调用 + 发布行为任一并集去重。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserMapper userMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final IdleItemMapper idleItemMapper;
    private final ActivityMapper activityMapper;
    private final LostFoundMapper lostFoundMapper;
    private final PostMapper postMapper;
    private final ReportMapper reportMapper;
    private final StudyPartnerMapper studyPartnerMapper;

    /**
     * 管理端待办数（第3项修复：后端准确 COUNT，不再由前端翻页推算）。
     * <p>口径：各审核类型 = 对应业务表 audit_status=0 的总数；report = 待处理举报数；
     * ai = 待审内容中 ai_risk_level>=1 的子集（与业务数同源，相加会重复计数，
     * 前端做待办角标合计时不得把 ai 再累加一遍）。
     */
    public Map<String, Long> pendingCounts() {
        Map<String, Long> m = new HashMap<>();
        m.put("idle", idleItemMapper.selectCount(new LambdaQueryWrapper<IdleItem>()
                .eq(IdleItem::getAuditStatus, Constants.AUDIT_PENDING)));
        m.put("activity", activityMapper.selectCount(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getAuditStatus, Constants.AUDIT_PENDING)));
        m.put("lostfound", lostFoundMapper.selectCount(new LambdaQueryWrapper<LostFound>()
                .eq(LostFound::getAuditStatus, Constants.AUDIT_PENDING)));
        m.put("post", postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getAuditStatus, Constants.AUDIT_PENDING)));
        m.put("partner", studyPartnerMapper.selectCount(new LambdaQueryWrapper<StudyPartner>()
                .eq(StudyPartner::getAuditStatus, Constants.AUDIT_PENDING)));
        m.put("report", reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getStatus, Constants.REPORT_PENDING)));
        long ai = 0;
        ai += idleItemMapper.selectCount(new LambdaQueryWrapper<IdleItem>()
                .eq(IdleItem::getAuditStatus, Constants.AUDIT_PENDING)
                .ge(IdleItem::getAiRiskLevel, 1));
        ai += activityMapper.selectCount(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getAuditStatus, Constants.AUDIT_PENDING)
                .ge(Activity::getAiRiskLevel, 1));
        ai += lostFoundMapper.selectCount(new LambdaQueryWrapper<LostFound>()
                .eq(LostFound::getAuditStatus, Constants.AUDIT_PENDING)
                .ge(LostFound::getAiRiskLevel, 1));
        ai += postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getAuditStatus, Constants.AUDIT_PENDING)
                .ge(Post::getAiRiskLevel, 1));
        ai += studyPartnerMapper.selectCount(new LambdaQueryWrapper<StudyPartner>()
                .eq(StudyPartner::getAuditStatus, Constants.AUDIT_PENDING)
                .ge(StudyPartner::getAiRiskLevel, 1));
        m.put("ai", ai);
        return m;
    }

    /** 数字卡片：总用户/今日活跃/今日AI调用/待审核数 */
    public StatsOverviewVO overview() {
        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setTotalUsers(userMapper.selectCount(null));

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        // 今日活跃：当日登录用户 并集 当日有AI调用的用户 并集 当日发布内容的用户
        java.util.Set<Long> active = new java.util.HashSet<>();
        userMapper.selectList(new LambdaQueryWrapper<User>()
                        .ge(User::getLastLoginTime, todayStart)
                        .select(User::getId))
                .forEach(u -> active.add(u.getId()));
        aiCallLogMapper.selectList(new LambdaQueryWrapper<AiCallLog>()
                        .ge(AiCallLog::getCreateTime, todayStart)
                        .select(AiCallLog::getUserId)
                        .groupBy(AiCallLog::getUserId))
                .forEach(l -> active.add(l.getUserId()));
        idleItemMapper.selectList(new LambdaQueryWrapper<IdleItem>()
                        .ge(IdleItem::getCreateTime, todayStart)
                        .select(IdleItem::getUserId)
                        .groupBy(IdleItem::getUserId))
                .forEach(i -> active.add(i.getUserId()));
        vo.setTodayActiveUsers((long) active.size());

        vo.setTodayAiCalls(aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .ge(AiCallLog::getCreateTime, todayStart)));

        long pending = idleItemMapper.selectCount(new LambdaQueryWrapper<IdleItem>()
                        .eq(IdleItem::getAuditStatus, Constants.AUDIT_PENDING))
                + activityMapper.selectCount(new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getAuditStatus, Constants.AUDIT_PENDING))
                + lostFoundMapper.selectCount(new LambdaQueryWrapper<LostFound>()
                        .eq(LostFound::getAuditStatus, Constants.AUDIT_PENDING))
                + postMapper.selectCount(new LambdaQueryWrapper<Post>()
                        .eq(Post::getAuditStatus, Constants.AUDIT_PENDING));
        vo.setPendingAudits(pending);
        return vo;
    }

    /**
     * 近30天趋势：用户增长 + AI调用 双折线。
     * 返回 {dates:[], userGrowth:[], aiCalls:[]}
     */
    public Map<String, Object> trend() {
        List<String> dates = new ArrayList<>();
        List<Long> userGrowth = new ArrayList<>();
        List<Long> aiCalls = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            dates.add(day.toString());
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
            // 用户累计增长（截至当日总数）
            userGrowth.add(userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .lt(User::getCreateTime, dayEnd)));
            // 当日 AI 调用量
            aiCalls.add(aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                    .ge(AiCallLog::getCreateTime, day.atStartOfDay())
                    .lt(AiCallLog::getCreateTime, dayEnd)));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("userGrowth", userGrowth);
        result.put("aiCalls", aiCalls);
        return result;
    }

    /**
     * 各模块发布量柱状图。
     * 返回 [{name:'闲置',value:n}, ...]
     */
    public List<Map<String, Object>> moduleStats() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(bar("闲置互换", idleItemMapper.selectCount(null)));
        list.add(bar("活动组队", activityMapper.selectCount(null)));
        list.add(bar("失物招领", lostFoundMapper.selectCount(null)));
        list.add(bar("动态广场", postMapper.selectCount(null)));
        list.add(bar("AI会话", aiCallLogMapper.selectCount(null)));
        return list;
    }

    /**
     * 饼图数据：失物状态分布 + 举报类型分布。
     * 返回 {lostStatus:[{name,value}], reportTypes:[{name,value}]}
     */
    public Map<String, Object> pieStats() {
        Map<String, Object> result = new HashMap<>();
        // 失物：进行中/已完成/已下架
        List<Map<String, Object>> lostStatus = new ArrayList<>();
        lostStatus.add(pie("进行中", lostFoundMapper.selectCount(
                new LambdaQueryWrapper<LostFound>().eq(LostFound::getStatus, Constants.LF_DOING))));
        lostStatus.add(pie("已完成", lostFoundMapper.selectCount(
                new LambdaQueryWrapper<LostFound>().eq(LostFound::getStatus, Constants.LF_DONE))));
        lostStatus.add(pie("已下架", lostFoundMapper.selectCount(
                new LambdaQueryWrapper<LostFound>().eq(LostFound::getStatus, Constants.LF_OFF))));
        result.put("lostStatus", lostStatus);
        // 举报类型分布
        List<Report> reports = reportMapper.selectList(
                new LambdaQueryWrapper<Report>().select(Report::getReasonType));
        Map<String, Long> typeCount = new HashMap<>();
        reports.forEach(r -> typeCount.merge(r.getReasonType(), 1L, Long::sum));
        List<Map<String, Object>> reportTypes = new ArrayList<>();
        typeCount.forEach((k, v) -> reportTypes.add(pie(k, v)));
        result.put("reportTypes", reportTypes);
        return result;
    }

    private Map<String, Object> bar(String name, Long value) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    private Map<String, Object> pie(String name, Long value) {
        return bar(name, value);
    }
}
