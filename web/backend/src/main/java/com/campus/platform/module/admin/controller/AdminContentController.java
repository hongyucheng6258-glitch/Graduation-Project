package com.campus.platform.module.admin.controller;

import com.campus.platform.common.R;
import com.campus.platform.module.admin.service.AdminContentService;
import com.campus.platform.module.admin.vo.SigninReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端内容管理：已通过内容下架/恢复、活动签到报表。
 */
@RestController
@RequestMapping("/api/admin/content")
@RequiredArgsConstructor
public class AdminContentController {

    private final AdminContentService adminContentService;

    /** 下架 */
    @PutMapping("/{type}/{id}/off")
    public R<Void> offline(@PathVariable String type, @PathVariable Long id) {
        adminContentService.offline(type, id);
        return R.ok();
    }

    /** 恢复上架 */
    @PutMapping("/{type}/{id}/on")
    public R<Void> online(@PathVariable String type, @PathVariable Long id) {
        adminContentService.online(type, id);
        return R.ok();
    }

    /** 活动签到报表 */
    @GetMapping("/activity/{id}/signin-report")
    public R<SigninReportVO> signinReport(@PathVariable Long id) {
        return R.ok(adminContentService.signinReport(id));
    }
}
