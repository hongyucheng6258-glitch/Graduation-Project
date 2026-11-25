package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.vo.StatsOverviewVO;
import com.campus.platform.module.admin.service.StatsService;

import com.campus.platform.common.Result;
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
    public Result<StatsOverviewVO> overview() {
        return Result.ok(statsService.overview());
    }

    /** 各类型待办数（后端准确 COUNT；ai 为待审子集，不参与角标合计） */
    @GetMapping("/pending-counts")
    public Result<Map<String, Long>> pendingCounts() {
        return Result.ok(statsService.pendingCounts());
    }

    @GetMapping("/trend")
    public Result<Map<String, Object>> trend() {
        return Result.ok(statsService.trend());
    }

    @GetMapping("/module")
    public Result<List<Map<String, Object>>> moduleStats() {
        return Result.ok(statsService.moduleStats());
    }

    @GetMapping("/pie")
    public Result<Map<String, Object>> pieStats() {
        return Result.ok(statsService.pieStats());
    }
}
