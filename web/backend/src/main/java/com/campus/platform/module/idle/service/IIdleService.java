package com.campus.platform.module.idle.service;

import com.campus.platform.common.mapper.MapperQueries;
import com.campus.platform.module.idle.mapper.IdleAppointmentMapper;
import com.campus.platform.module.idle.vo.PendingAppointmentVO;
import com.campus.platform.module.idle.mapper.IdleReviewMapper;
import com.campus.platform.module.idle.entity.IdleAppointment;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.idle.dto.IdlePublishDTO;
import com.campus.platform.module.idle.vo.AppointmentVO;
import com.campus.platform.module.idle.vo.IdleDetailVO;
import com.campus.platform.module.idle.dto.AppointDTO;
import com.campus.platform.module.idle.vo.IdleItemVO;
import com.campus.platform.module.idle.vo.IdleGuideCard;
import com.campus.platform.module.idle.entity.IdleReview;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.ai.service.IContentAiAuditService;
import com.campus.platform.module.message.service.IMessageService;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.utils.ImageListJson;
import com.campus.platform.utils.ListedImages;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.post.dto.ReviewDTO;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * IdleService 服务接口。
 */
public interface IIdleService {

    IdleItem publish(Long userId, IdlePublishDTO dto);

    PageResult<IdleItemVO> list(String keyword, String category, int pageNum, int pageSize);

    List<IdleGuideCard> guideSearch(Long uid, String keyword, String category, String exchange, int limit);

    IdleGuideCard guideDetail(Long uid, long itemId);

    List<IdleGuideCard> guideCards(Long uid, List<Long> itemIds);

    IdleDetailVO detail(Long id, Long currentUid);

    IdleItem update(Long userId, Long id, IdlePublishDTO dto);

    void offline(Long userId, Long id);

    void relist(Long userId, Long id);

    PageResult<IdleItemVO> myList(Long userId, int pageNum, int pageSize);

    IdleAppointment appoint(Long userId, Long itemId, AppointDTO dto);

    void handleAppoint(Long userId, Long appointId, boolean accept);

    void cancelAppoint(Long userId, Long appointId);

    void finishAppoint(Long userId, Long appointId);

    void review(Long userId, Long appointId, ReviewDTO dto);

    PageResult<AppointmentVO> myAppointments(Long userId, String role, int pageNum, int pageSize);

    PageResult<AppointmentVO> myAppointments(Long userId, String role, int pageNum, int pageSize, Long appointmentId);
}
