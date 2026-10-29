package com.campus.platform.module.activity.service;

import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.activity.entity.Activity;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.campus.platform.common.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 活动状态定时清理（兜底，非实时保障）：
 * 实时展示状态由 {@code ActivityService.resolveDisplayStatus} 动态计算；
 * 本任务每分钟把数据库中已过结束时间的活动落库为「已结束」，避免脏数据长期滞留。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityStatusSyncTask {

    private final ActivityMapper activityMapper;

    @Scheduled(fixedRate = 60000)
    public void syncExpiredActivities() {
        LocalDateTime now = LocalDateTime.now();
        try {
            int updated = activityMapper.update(null,
                    new UpdateWrapper<Activity>()
                            .in("status", Constants.ACTIVITY_SIGNING, Constants.ACTIVITY_FULL)
                            .and(w -> w.isNotNull("end_time").le("end_time", now))
                            .set("status", Constants.ACTIVITY_ENDED));
            if (updated > 0) {
                log.info("活动状态同步：{} 个已结束活动更新为已结束", updated);
            }
        } catch (Exception e) {
            log.error("活动状态同步失败", e);
        }
    }
}
