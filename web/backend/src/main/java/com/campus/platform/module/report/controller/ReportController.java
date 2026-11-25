package com.campus.platform.module.report.controller;

import com.campus.platform.module.report.service.ReportService;
import com.campus.platform.module.report.dto.ReportDTO;
import com.campus.platform.module.report.entity.Report;
import com.campus.platform.common.PageResult;

import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @PostMapping
    public Result<Report> submit(@Valid @RequestBody ReportDTO dto) {
        return Result.ok(reportService.submit(UserContext.getUid(), dto));
    }

    @GetMapping("/my")
    public Result<PageResult<Report>> my(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(reportService.myList(UserContext.getUid(), pageNum, pageSize));
    }
}
