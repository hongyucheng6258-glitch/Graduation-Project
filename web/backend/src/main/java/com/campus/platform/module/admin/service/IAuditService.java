package com.campus.platform.module.admin.service;

import com.campus.platform.module.message.service.IMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.partner.entity.StudyPartner;
import com.campus.platform.module.partner.mapper.StudyPartnerMapper;
import com.campus.platform.module.post.entity.Post;
import com.campus.platform.module.post.mapper.PostMapper;
import com.campus.platform.module.qa.entity.CampusQuestion;
import com.campus.platform.module.qa.mapper.CampusQuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/**
 * AuditService 服务接口。
 */
public interface IAuditService {

    PageResult<?> pendingList(String type, int pageNum, int pageSize);

    PageResult<?> pendingList(String type, int pageNum, int pageSize, boolean riskOnly);

    IdleItem idleDetail(Long id);

    Activity activityDetail(Long id);

    PageResult<?> allList(String type, String keyword, Long record, int pageNum, int pageSize);

    void pass(String type, Long id);

    void pass(String type, Long id, boolean pendingOnly);

    void reject(String type, Long id, String reason);

    void reject(String type, Long id, String reason, boolean pendingOnly);

    int reviewPendingIdle(Long id, boolean pass, String reason);

    int reviewPendingActivity(Long id, boolean pass, String reason);
}
