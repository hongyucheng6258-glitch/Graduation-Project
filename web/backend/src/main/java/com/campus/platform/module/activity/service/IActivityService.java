package com.campus.platform.module.activity.service;

import com.campus.platform.common.mapper.MapperQueries;
import com.campus.platform.module.activity.mapper.ActivityMemberMapper;
import com.campus.platform.module.activity.mapper.ActivitySigninMapper;
import com.campus.platform.module.activity.dto.ActivityPublishDTO;
import com.campus.platform.module.activity.vo.ActivityDetailVO;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.activity.entity.ActivityMember;
import com.campus.platform.module.activity.entity.ActivitySignin;
import com.campus.platform.module.activity.vo.ActivityVO;
import com.campus.platform.module.activity.dto.SigninDTO;
import com.campus.platform.module.activity.dto.SignupDTO;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.vo.MemberVO;
import com.campus.platform.module.ai.service.IContentAiAuditService;
import com.campus.platform.module.message.service.IMessageService;
import com.campus.platform.utils.ImageListJson;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.utils.ListedImages;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.config.SystemConfigHolder;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import com.campus.platform.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ActivityService 服务接口。
 */
public interface IActivityService {

    void ensurePublishingEnabled();

    Activity publish(Long userId, ActivityPublishDTO dto);

    Activity update(Long userId, Long id, ActivityPublishDTO dto);

    PageResult<ActivityVO> list(String keyword, String category, int pageNum, int pageSize);

    PageResult<ActivityVO> list(String keyword, String category, int pageNum, int pageSize, LocalDateTime startInclusive, LocalDateTime endExclusive);

    ActivityDetailVO detail(Long id, Long currentUid);

    void signup(Long userId, Long activityId, SignupDTO dto);

    void acceptWaitlist(Long userId, Long activityId, Long entryId, SignupDTO dto);

    void cancelSignup(Long userId, Long activityId);

    PageResult<MemberVO> members(Long userId, Long activityId, int pageNum, int pageSize);

    void handleMember(Long userId, Long memberId, boolean approve);

    String signinQrCode(Long userId, Long activityId);

    void signin(Long userId, SigninDTO dto);

    PageResult<ActivityVO> myPublished(Long userId, int pageNum, int pageSize);

    PageResult<MemberVO> mySignups(Long userId, int pageNum, int pageSize);
}
