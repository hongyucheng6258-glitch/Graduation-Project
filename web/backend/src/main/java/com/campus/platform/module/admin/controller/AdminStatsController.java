package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.vo.StatsOverviewVO;
import com.campus.platform.module.admin.service.StatsService;

import com.campus.platform.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {
    private final StatsService statsService;

    @GetMapping("/overview")
    public R<StatsOverviewVO> overview() {
        return R.ok(statsService.overview());
    }

    /** 各类型待办数（后端准确 COUNT；ai 为待审子集，不参与角标合计） */
    @GetMapping("/pending-counts")
    public R<Map<String, Long>> pendingCounts() {
        return R.ok(statsService.pendingCounts());
    }

    @GetMapping("/trend")
    public R<Map<String, Object>> trend() {
        return R.ok(statsService.trend());
    }

    @GetMapping("/module")
    public R<List<Map<String, Object>>> moduleStats() {
        return R.ok(statsService.moduleStats());
    }

    @GetMapping("/pie")
    public R<Map<String, Object>> pieStats() {
        return R.ok(statsService.pieStats());
    }
}
