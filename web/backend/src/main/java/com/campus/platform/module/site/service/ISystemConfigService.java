package com.campus.platform.module.site.service;

import com.campus.platform.module.site.entity.SystemConfig;

import java.util.List;
import java.util.Map;

/**
 * 通用系统配置服务接口。
 */
public interface ISystemConfigService {

    /** 读取全部配置（按 category + sort 排序） */
    List<SystemConfig> listAll();

    /** 按分组读取配置 */
    List<SystemConfig> listByCategory(String category);

    /** 批量更新配置值 → 事务提交后刷新缓存即时生效 */
    void updateConfigs(Map<String, String> configs);

    /** 新增配置项 */
    SystemConfig create(SystemConfig config);

    /** 更新配置项（含元信息：类型/分组/说明/排序） */
    SystemConfig update(Long id, SystemConfig config);

    /** 删除配置项（内置关键键不可删除） */
    void delete(Long id);

    /** 手动刷新缓存 */
    void refresh();
}
