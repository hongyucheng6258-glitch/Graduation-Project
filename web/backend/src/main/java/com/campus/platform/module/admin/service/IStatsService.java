package com.campus.platform.module.admin.service;

import com.campus.platform.module.admin.vo.StatsOverviewVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.common.Constants;
import com.campus.platform.common.DailyCount;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.ai.entity.AiCallLog;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.post.entity.Post;
import com.campus.platform.module.report.entity.Report;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.ai.mapper.AiCallLogMapper;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.post.mapper.PostMapper;
import com.campus.platform.module.report.mapper.ReportMapper;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.module.partner.entity.StudyPartner;
import com.campus.platform.module.partner.mapper.StudyPartnerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * StatsService 服务接口。
 */
public interface IStatsService {

    Map<String, Long> pendingCounts();

    StatsOverviewVO overview();

    Map<String, Object> trend();

    List<Map<String, Object>> moduleStats();

    Map<String, Object> pieStats();
}
