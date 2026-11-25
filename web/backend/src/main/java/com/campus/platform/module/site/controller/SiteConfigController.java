package com.campus.platform.module.site.controller;

import com.campus.platform.common.Result;
import com.campus.platform.config.SystemConfigHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公开的站点配置接口（无需登录），供前端获取站点名称、标语、维护状态等。
 */
@RestController
@RequestMapping("/api/site")
@RequiredArgsConstructor
public class SiteConfigController {

    private final SystemConfigHolder systemConfigHolder;

    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        return Result.ok(Map.of(
                "siteName", systemConfigHolder.getSiteName(),
                "siteSlogan", systemConfigHolder.get("site_slogan"),
                "maintenance", systemConfigHolder.isMaintenanceMode(),
                "registerEnabled", systemConfigHolder.isRegisterEnabled()
        ));
    }
}
