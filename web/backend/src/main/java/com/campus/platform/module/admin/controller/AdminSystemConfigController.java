package com.campus.platform.module.admin.controller;

import com.campus.platform.common.R;
import com.campus.platform.module.admin.service.AdminPermissionService;
import com.campus.platform.module.site.entity.SystemConfig;
import com.campus.platform.module.site.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端-通用系统配置接口。
 * 所有配置修改后即时生效（通过 SystemConfigHolder.refresh() 热更新），无需重启服务。
 * 路径：/api/admin/system/config
 * <p>P1 修复：系统配置读写、刷新、删除均为超级管理员专属操作；
 * 普通管理员（audit）即使绕过前端导航，直接请求本接口也会被服务端拒绝。
 */
@RestController
@RequestMapping("/api/admin/system/config")
@RequiredArgsConstructor
public class AdminSystemConfigController {

    private final SystemConfigService systemConfigService;
    private final AdminPermissionService adminPermissionService;

    /** 获取全部系统配置（按分组排序） */
    @GetMapping
    public R<List<SystemConfig>> list(
            @RequestParam(required = false) String category) {
        adminPermissionService.requireSuper();
        return R.ok(systemConfigService.listByCategory(category));
    }

    /** 批量更新配置值（最常用：管理端表单保存）→ 即时生效 */
    @PutMapping
    public R<Void> updateConfigs(@RequestBody Map<String, String> configs) {
        adminPermissionService.requireSuper();
        systemConfigService.updateConfigs(configs);
        return R.ok();
    }

    /** 新增配置项 */
    @PostMapping
    public R<SystemConfig> create(@RequestBody SystemConfig config) {
        adminPermissionService.requireSuper();
        return R.ok(systemConfigService.create(config));
    }

    /** 更新配置项（含元信息） */
    @PutMapping("/{id}")
    public R<SystemConfig> update(@PathVariable Long id, @RequestBody SystemConfig config) {
        adminPermissionService.requireSuper();
        return R.ok(systemConfigService.update(id, config));
    }

    /** 删除配置项 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        adminPermissionService.requireSuper();
        systemConfigService.delete(id);
        return R.ok();
    }

    /** 手动刷新配置缓存 */
    @PostMapping("/refresh")
    public R<Void> refresh() {
        adminPermissionService.requireSuper();
        systemConfigService.refresh();
        return R.ok();
    }
}
