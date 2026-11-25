package com.campus.platform.module.activity.service;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.activity.mapper.ActivityScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * ActivityScheduleService 服务接口。
 */
public interface IActivityScheduleService {

    /** 校园时区 */
    ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 日程区间与条目 */
    record Schedule(LocalDate from, LocalDate through, List<Entry> entries) {}

    /** 日程条目 */
    record Entry(long activityId, String title, String location, LocalDateTime startTime,
                 LocalDateTime endTime, int signupStatus) {}

    /** 冲突检测结果 */
    record ConflictCheck(boolean timeKnown, List<Conflict> conflicts) {}

    /** 时间冲突区间 */
    record Conflict(long activityId, String title, LocalDateTime startTime,
                    LocalDateTime endTime, long overlapSeconds) {}

    Schedule schedule(Long userId);

    ConflictCheck conflicts(Long userId, Long activityId);
}
