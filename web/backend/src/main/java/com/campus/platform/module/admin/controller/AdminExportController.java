package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.service.AdminExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * 管理端导出：活动报名名单 / 签到名单 CSV。
 */
@RestController
@RequestMapping("/api/admin/export")
@RequiredArgsConstructor
public class AdminExportController {

    private final AdminExportService adminExportService;

    @GetMapping("/activity/{id}/members")
    public void exportMembers(@PathVariable Long id, HttpServletResponse response) throws IOException {
        adminExportService.exportMembers(id, response);
    }

    @GetMapping("/activity/{id}/signins")
    public void exportSignins(@PathVariable Long id, HttpServletResponse response) throws IOException {
        adminExportService.exportSignins(id, response);
    }
}
