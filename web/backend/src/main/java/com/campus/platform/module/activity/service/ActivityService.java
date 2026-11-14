package com.campus.platform.module.activity.service;

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

import com.campus.platform.module.ai.service.ContentAiAuditService;
import com.campus.platform.module.message.service.MessageService;
import com.campus.platform.module.idle.service.IdleService;

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
import com.campus.platform.utils.SignTokenUtils;
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
 * 活动组队服务（C2/C3）：发布→报名审批→扫码签到。
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityMapper activityMapper;
    private final ActivityMemberMapper memberMapper;
    private final ActivitySigninMapper signinMapper;
    private final UserMapper userMapper;
    private final MessageService messageService;
    private final SignTokenUtils signTokenUtils;
    private final SensitiveWordService sensitiveWordService;
    private final ContentAiAuditService contentAiAuditService;
    private final SystemConfigHolder systemConfigHolder;
    private final ActivityWaitlist waitlist;

    @Value("${CAMPUS_ADMIN_ASSISTANT_ENABLED:false}")
    private boolean versionedAudit;

    public void ensurePublishingEnabled() {
        if (!systemConfigHolder.isActivityPublishEnabled()) {
            throw new BizException(ResultCode.FORBIDDEN, "当前已关闭活动发布，请联系管理员");
        }
    }

    /** 发布活动（待审核） */
    @Transactional
    public Activity publish(Long userId, ActivityPublishDTO dto) {
        ensurePublishingEnabled();
        if (sensitiveWordService.contains(dto.getTitle()) || sensitiveWordService.contains(dto.getDescription())) {
            throw new BizException(ResultCode.SENSITIVE_WORD);
        }
        if (dto.getStartTime() != null && dto.getEndTime() != null
                && dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "结束时间不能早于开始时间");
        }
        if (dto.getSignupDeadline() != null && dto.getStartTime() != null
                && dto.getSignupDeadline().isAfter(dto.getStartTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "报名截止时间不能晚于活动开始时间");
        }
        if (dto.getSignupDeadline() != null && dto.getEndTime() != null
                && dto.getSignupDeadline().isAfter(dto.getEndTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "报名截止时间不能晚于活动结束时间");
        }
        Activity activity = new Activity();
        BeanUtil.copyProperties(dto, activity);
        activity.setUserId(userId);
        activity.setImages(IdleService.toJson(dto.getImages()));
        activity.setAuditStatus(Constants.AUDIT_PENDING);
        activity.setStatus(Constants.ACTIVITY_SIGNING);
        activityMapper.insert(activity);
        contentAiAuditService.audit(Constants.BIZ_ACTIVITY, activity, userId, dto.getTitle(), dto.getDescription());
        return activity;
    }

    /** 编辑活动（仅发布者本人；已结束不可编辑；编辑后重新进入 AI 审核） */
    @Transactional
    public Activity update(Long userId, Long id, ActivityPublishDTO dto) {
        Activity activity = activityMapper.selectForUpdate(id);
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        if (!activity.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能编辑自己发布的活动");
        }
        if (activity.getStatus() == Constants.ACTIVITY_ENDED) {
            throw new BizException(ResultCode.BAD_REQUEST, "已结束的活动不可编辑");
        }
        if (sensitiveWordService.contains(dto.getTitle()) || sensitiveWordService.contains(dto.getDescription())) {
            throw new BizException(ResultCode.SENSITIVE_WORD);
        }
        if (dto.getStartTime() != null && dto.getEndTime() != null
                && dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "结束时间不能早于开始时间");
        }
        if (dto.getSignupDeadline() != null && dto.getStartTime() != null
                && dto.getSignupDeadline().isAfter(dto.getStartTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "报名截止时间不能晚于活动开始时间");
        }
        if (dto.getSignupDeadline() != null && dto.getEndTime() != null
                && dto.getSignupDeadline().isAfter(dto.getEndTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "报名截止时间不能晚于活动结束时间");
        }
        activity.setTitle(dto.getTitle());
        activity.setCategory(dto.getCategory());
        activity.setDescription(dto.getDescription());
        activity.setLocation(dto.getLocation());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity.setSignupDeadline(dto.getSignupDeadline());
        Integer capacity = dto.getMaxMembers();
        if (capacity != null && capacity > 0 && memberMapper.selectCount(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getActivityId, id)
                .in(ActivityMember::getStatus, Constants.MEMBER_PENDING, Constants.MEMBER_APPROVED)) > capacity) {
            throw new BizException(ResultCode.BAD_REQUEST, "人数上限不能小于待审批及已通过的报名人数");
        }
        activity.setMaxMembers(capacity);
        activity.setImages(IdleService.toJson(dto.getImages()));
        activity.setAuditStatus(Constants.AUDIT_PENDING);
        activity.setAuditReason(null);
        activityMapper.updateById(activity);
        if (versionedAudit) {
            activityMapper.updateAuditRevision(id, UUID.randomUUID().toString());
        }
        contentAiAuditService.audit(Constants.BIZ_ACTIVITY, activity, userId, dto.getTitle(), dto.getDescription());
        return activity;
    }

    /** 列表检索（公开，仅审核通过；已结束的活动不展示） */
    public PageResult<ActivityVO> list(String keyword, String category, int pageNum, int pageSize) {
        return list(keyword, category, pageNum, pageSize, null, null);
    }

    public PageResult<ActivityVO> list(String keyword, String category, int pageNum, int pageSize,
                                      LocalDateTime startInclusive, LocalDateTime endExclusive) {
        LocalDateTime now = LocalDateTime.now();
        Page<Activity> page = activityMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getAuditStatus, Constants.AUDIT_PASS)
                        .and(w -> w.eq(Activity::getStatus, Constants.ACTIVITY_SIGNING)
                                .or().eq(Activity::getStatus, Constants.ACTIVITY_FULL))
                        // 时间已结束的活动不再展示（endTime 为空视为长期活动）
                        .and(w -> w.isNull(Activity::getEndTime)
                                .or().gt(Activity::getEndTime, now))
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(Activity::getTitle, keyword)
                                .or().like(Activity::getDescription, keyword))
                        .eq(StrUtil.isNotBlank(category), Activity::getCategory, category)
                        .ge(startInclusive != null, Activity::getStartTime, startInclusive)
                        .lt(endExclusive != null, Activity::getStartTime, endExclusive)
                        .orderByDesc(Activity::getId));
        return toPage(page);
    }

    /** 详情（含报名数、我的报名状态） */
    public ActivityDetailVO detail(Long id, Long currentUid) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        boolean isOwner = currentUid != null && currentUid.equals(activity.getUserId());
        if (activity.getAuditStatus() != Constants.AUDIT_PASS && !isOwner) {
            throw new BizException(ResultCode.AUDIT_PENDING);
        }
        ActivityDetailVO vo = new ActivityDetailVO();
        BeanUtil.copyProperties(toVO(activity), vo);
        vo.setIsOwner(isOwner);
        vo.setSigninOpen(signinOpen(activity, LocalDateTime.now()));
        if (currentUid != null && !isOwner) {
            ActivityMember my = memberMapper.selectOne(new LambdaQueryWrapper<ActivityMember>()
                    .eq(ActivityMember::getActivityId, id)
                    .eq(ActivityMember::getUserId, currentUid)
                    .last("LIMIT 1"));
            vo.setMySignupStatus(my == null ? null : my.getStatus());
            vo.setSignedIn(signinMapper.selectCount(new LambdaQueryWrapper<ActivitySignin>()
                    .eq(ActivitySignin::getActivityId, id)
                    .eq(ActivitySignin::getUserId, currentUid)) > 0);
        } else {
            vo.setSignedIn(false);
        }
        return vo;
    }

    /**
     * 报名（审批制，联合唯一索引防重复报名）。
     * 时间/人数/下架等校验统一走 checkSignAllowed（最终安全边界）。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void signup(Long userId, Long activityId, SignupDTO dto) {
        Activity activity = activityMapper.selectForUpdate(activityId);
        checkSignAllowed(activityId, activity);
        waitlist.refresh(activity);
        if (waitlist.queued(activityId, userId) || waitlist.reservedForOthers(activity, userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请从候补入口操作，当前空位优先用于递补");
        }
        createSignup(userId, activity, dto);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void acceptWaitlist(Long userId, Long activityId, Long entryId, SignupDTO dto) {
        Activity activity = activityMapper.selectForUpdate(activityId);
        if (waitlist.claim(activity, userId, entryId)) {
            createSignup(userId, activity, dto);
        }
    }

    private void createSignup(Long userId, Activity activity, SignupDTO dto) {
        Long activityId = activity.getId();
        if (activity.getUserId().equals(userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能报名自己发布的活动");
        }
        Long exist = memberMapper.selectCount(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getActivityId, activityId)
                .eq(ActivityMember::getUserId, userId));
        if (exist > 0) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "你已报名该活动，请等待审批");
        }
        ActivityMember member = new ActivityMember();
        member.setActivityId(activityId);
        member.setUserId(userId);
        member.setRemark(dto.getRemark());
        member.setStatus(Constants.MEMBER_PENDING);
        try {
            memberMapper.insert(member);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发下唯一索引兜底：重复报名转友好提示
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "你已报名该活动，请等待审批");
        }

        User applicant = userMapper.selectById(userId);
        messageService.send(activity.getUserId(), Constants.MSG_INTERACT,
                "活动有新报名",
                String.format("「%s」报名了你的活动「%s」，请审批。",
                        applicant == null ? "有用户" : applicant.getNickname(), activity.getTitle()),
                Constants.BIZ_ACTIVITY, activityId);
        messageService.send(userId, Constants.MSG_INTERACT,
                "报名已提交",
                String.format("你对活动「%s」的报名已提交，等待发布者审批。", activity.getTitle()),
                Constants.BIZ_ACTIVITY, activityId);
    }

    /** 取消报名（仅待审批/已通过可取消；已结束/已签到不可取消） */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelSignup(Long userId, Long activityId) {
        Activity activity = activityMapper.selectForUpdate(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        if (activity.getStatus() != null && activity.getStatus() == Constants.ACTIVITY_ENDED) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动已结束，无法取消报名");
        }
        ActivityMember member = memberMapper.selectOne(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getActivityId, activityId)
                .eq(ActivityMember::getUserId, userId));
        if (member == null) {
            throw new BizException(ResultCode.NOT_FOUND, "你尚未报名该活动");
        }
        boolean signed = signinMapper.selectCount(new LambdaQueryWrapper<ActivitySignin>()
                .eq(ActivitySignin::getActivityId, activityId)
                .eq(ActivitySignin::getUserId, userId)) > 0;
        if (signed) {
            throw new BizException(ResultCode.BAD_REQUEST, "你已签到，无法取消报名");
        }
        memberMapper.deleteById(member.getId());
        waitlist.release(activityId, userId, "CANCELLED");
        waitlist.refresh(activity);
        // 人数回退：已报满 -> 恢复报名中
        activityMapper.replaceStatus(activityId, Constants.ACTIVITY_FULL, Constants.ACTIVITY_SIGNING);
        messageService.send(activity.getUserId(), Constants.MSG_INTERACT,
                "报名已取消",
                String.format("有同学取消了活动「%s」的报名。", activity.getTitle()),
                Constants.BIZ_ACTIVITY, activityId);
    }
    /** 报名名单（仅发布者可见，含签到状态；第8项修复：分页返回） */
    public PageResult<MemberVO> members(Long userId, Long activityId, int pageNum, int pageSize) {
        checkPublisher(userId, activityId);
        Page<ActivityMember> page = memberMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ActivityMember>()
                        .eq(ActivityMember::getActivityId, activityId)
                        .orderByAsc(ActivityMember::getId));
        List<ActivityMember> list = page.getRecords();
        Map<Long, User> userMap = list.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(list.stream().map(ActivityMember::getUserId).toList())
                        .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Boolean> signedMap = signinMapper.selectList(new LambdaQueryWrapper<ActivitySignin>()
                        .eq(ActivitySignin::getActivityId, activityId))
                .stream().collect(Collectors.toMap(ActivitySignin::getUserId, s -> true));
        java.util.function.Function<ActivityMember, MemberVO> toVo = m -> {
            MemberVO vo = new MemberVO();
            BeanUtil.copyProperties(m, vo);
            User u = userMap.get(m.getUserId());
            vo.setNickname(u == null ? "" : u.getNickname());
            vo.setAvatar(u == null ? null : u.getAvatar());
            vo.setStudentNo(u == null ? null : u.getStudentNo());
            vo.setSignedIn(signedMap.getOrDefault(m.getUserId(), false));
            return vo;
        };
        PageResult<MemberVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setList(page.getRecords().stream().map(toVo).collect(Collectors.toList()));
        return result;
    }

    /**
     * 审批报名（通过/拒绝）→ 消息通知；满员自动更新活动状态。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleMember(Long userId, Long memberId, boolean approve) {
        ActivityMember member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BizException(ResultCode.NOT_FOUND, "报名记录不存在");
        }
        // Serialize approvals for this activity, including approvals for different members.
        Activity activity = activityMapper.selectForUpdate(member.getActivityId());
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        if (!activity.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只有活动发布者可以执行此操作");
        }
        // The member may have been handled or cancelled while waiting for the lock.
        member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BizException(ResultCode.NOT_FOUND, "报名记录不存在");
        }
        if (member.getStatus() != Constants.MEMBER_PENDING) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该报名已审批");
        }
        // 活动已结束/已下架时不允许再审批（防止与定时状态清理竞争时误放行）
        if (activity.getStatus() == Constants.ACTIVITY_OFF) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动已下架，无法审批");
        }
        if (activity.getEndTime() != null && !LocalDateTime.now().isBefore(activity.getEndTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动已结束，无法审批");
        }
        waitlist.refresh(activity);
        // 兼容上线前已超量积压的待审记录，审批仍以实际通过人数为上限。
        if (approve && activity.getMaxMembers() != null && activity.getMaxMembers() > 0) {
            Long approved = memberMapper.selectCount(new LambdaQueryWrapper<ActivityMember>()
                    .eq(ActivityMember::getActivityId, activity.getId())
                    .eq(ActivityMember::getStatus, Constants.MEMBER_APPROVED));
            if (approved >= activity.getMaxMembers()) {
                throw new BizException(ResultCode.BAD_REQUEST, "活动人数已满");
            }
            if (waitlist.offersFillRemainingSeats(activity, member.getUserId(), approved)) {
                throw new BizException(ResultCode.BAD_REQUEST, "空位已为候补学生保留");
            }
        }
        member.setStatus(approve ? Constants.MEMBER_APPROVED : Constants.MEMBER_REJECTED);
        int changed = memberMapper.update(null, new LambdaUpdateWrapper<ActivityMember>()
                .eq(ActivityMember::getId, memberId)
                .eq(ActivityMember::getStatus, Constants.MEMBER_PENDING)
                .set(ActivityMember::getStatus, member.getStatus()));
        if (changed != 1) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该报名已审批或已取消");
        }
        waitlist.release(activity.getId(), member.getUserId(), approve ? "APPROVED" : "REJECTED");
        if (!approve) waitlist.refresh(activity);
        // 满员自动变更状态
        if (approve && activity.getMaxMembers() != null && activity.getMaxMembers() > 0) {
            Long approved = memberMapper.selectCount(new LambdaQueryWrapper<ActivityMember>()
                    .eq(ActivityMember::getActivityId, activity.getId())
                    .eq(ActivityMember::getStatus, Constants.MEMBER_APPROVED));
            if (approved >= activity.getMaxMembers()) {
                activity.setStatus(Constants.ACTIVITY_FULL);
                activityMapper.updateById(activity);
            }
        }
        messageService.send(member.getUserId(), Constants.MSG_AUDIT,
                approve ? "报名已通过" : "报名未通过",
                String.format("你对活动「%s」的报名%s。",
                        activity.getTitle(), approve ? "已通过，记得准时参加并扫码签到" : "未通过"),
                Constants.BIZ_ACTIVITY, activity.getId());
    }

    private boolean signinOpen(Activity activity, LocalDateTime now) {
        return activity.getAuditStatus() == Constants.AUDIT_PASS
                && activity.getStatus() != Constants.ACTIVITY_OFF
                && activity.getStatus() != Constants.ACTIVITY_ENDED
                && activity.getStartTime() != null
                && !now.isBefore(activity.getStartTime())
                && (activity.getEndTime() == null || now.isBefore(activity.getEndTime()));
    }

    /** 发布者获取签到二维码内容（campus://signin/{id}/{token}） */
    public String signinQrCode(Long userId, Long activityId) {
        checkPublisher(userId, activityId);
        return signTokenUtils.generateQrContent(activityId);
    }

    /**
     * 扫码签到：token 校验 + 必须为已通过报名的成员 + 防重复签到。
     */
    @Transactional
    public void signin(Long userId, SigninDTO dto) {
        if (!signTokenUtils.verify(dto.getActivityId(), dto.getToken())) {
            throw new BizException(ResultCode.BAD_REQUEST, "签到二维码无效");
        }
        Activity activity = activityMapper.selectForUpdate(dto.getActivityId());
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        if (!signinOpen(activity, LocalDateTime.now())) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅可在活动开始后、结束前签到");
        }
        ActivityMember member = memberMapper.selectOne(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getActivityId, dto.getActivityId())
                .eq(ActivityMember::getUserId, userId)
                .last("LIMIT 1"));
        if (member == null || member.getStatus() != Constants.MEMBER_APPROVED) {
            throw new BizException(ResultCode.FORBIDDEN, "报名未通过，无法签到");
        }
        Long exist = signinMapper.selectCount(new LambdaQueryWrapper<ActivitySignin>()
                .eq(ActivitySignin::getActivityId, dto.getActivityId())
                .eq(ActivitySignin::getUserId, userId));
        if (exist > 0) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "你已签到，请勿重复操作");
        }
        ActivitySignin signin = new ActivitySignin();
        signin.setActivityId(dto.getActivityId());
        signin.setUserId(userId);
        signinMapper.insert(signin);
        // 通知发布者：有人已扫码签到
        User signer = userMapper.selectById(userId);
        messageService.send(activity.getUserId(), Constants.MSG_INTERACT,
                "成员已签到",
                String.format("「%s」已扫码签到你的活动「%s」。",
                        signer == null ? "有成员" : signer.getNickname(), activity.getTitle()),
                Constants.BIZ_ACTIVITY, dto.getActivityId());
    }

    /** 我的发布 */
    public PageResult<ActivityVO> myPublished(Long userId, int pageNum, int pageSize) {
        Page<Activity> page = activityMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getUserId, userId)
                        .orderByDesc(Activity::getId));
        return toPage(page);
    }

    /** 我的报名 */
    public PageResult<MemberVO> mySignups(Long userId, int pageNum, int pageSize) {
        Page<ActivityMember> page = memberMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ActivityMember>()
                        .eq(ActivityMember::getUserId, userId)
                        .orderByDesc(ActivityMember::getId));
        List<ActivityMember> records = page.getRecords();
        Map<Long, Activity> actMap = records.isEmpty() ? Map.of() :
                activityMapper.selectBatchIds(records.stream().map(ActivityMember::getActivityId).toList())
                        .stream().collect(Collectors.toMap(Activity::getId, Function.identity()));
        return PageResult.of(page, m -> {
            MemberVO vo = new MemberVO();
            BeanUtil.copyProperties(m, vo);
            Activity a = actMap.get(m.getActivityId());
            vo.setActivityTitle(a == null ? "活动已删除" : a.getTitle());
            return vo;
        });
    }

    // ---------- 内部方法 ----------

    /**
     * 报名资格最终校验（安全边界，不能只依赖前端按钮）。
     * 顺序：不存在/未审核 → 已下架 → 已结束 → 报名已截止 → 已开始 → 人数已满。
     * signupDeadline 为空时，默认截止时间 = startTime（活动开始后不允许新报名）。
     */
    private void checkSignAllowed(Long activityId, Activity activity) {
        if (activity == null || activity.getAuditStatus() != Constants.AUDIT_PASS) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在或未通过审核");
        }
        if (activity.getStatus() == Constants.ACTIVITY_OFF) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动已下架");
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getEndTime() != null && !now.isBefore(activity.getEndTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动已结束");
        }
        if (activity.getSignupDeadline() != null && !now.isBefore(activity.getSignupDeadline())) {
            throw new BizException(ResultCode.BAD_REQUEST, "报名已截止");
        }
        // signupDeadline 为空 → 默认截止为 startTime
        if (activity.getStartTime() != null && !now.isBefore(activity.getStartTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动已开始，无法报名");
        }
        if (activity.getMaxMembers() == null || activity.getMaxMembers() <= 0) return;
        if (memberMapper.selectCount(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getActivityId, activityId)
                .in(ActivityMember::getStatus, Constants.MEMBER_PENDING, Constants.MEMBER_APPROVED)) >= activity.getMaxMembers()) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动人数已满");
        }
    }

    private Activity checkPublisher(Long userId, Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        if (!activity.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只有活动发布者可以执行此操作");
        }
        return activity;
    }

    private PageResult<ActivityVO> toPage(Page<Activity> page) {
        List<Activity> activities = page.getRecords();
        Map<Long, User> publishers = userMapper.findPublisherProfiles(
                activities.stream().map(Activity::getUserId).distinct().toList());
        var capacity = memberMapper.capacityByActivities(activities.stream().map(Activity::getId).toList());
        LocalDateTime now = LocalDateTime.now();
        return PageResult.of(page, activity -> {
            var count = capacity.get(activity.getId());
            var card = toVO(activity, publishers.get(activity.getUserId()),
                    count == null ? 0 : count.approved(), count == null ? 0 : count.pending(), now);
            if (Objects.equals(activity.getAuditStatus(), Constants.AUDIT_PASS)
                    && !Objects.equals(activity.getStatus(), Constants.ACTIVITY_OFF)) {
                card.setImageList(ListedImages.urls("activity", activity.getId(), card.getImageList()));
                card.setImages(IdleService.toJson(card.getImageList()));
                card.setPublisherAvatar(ListedImages.avatar(activity.getUserId(), card.getPublisherAvatar()));
            }
            return card;
        });
    }

    private ActivityVO toVO(Activity activity) {
        var count = memberMapper.capacityCounts(activity.getId());
        return toVO(activity, userMapper.selectById(activity.getUserId()), count.approved(), count.pending(),
                LocalDateTime.now());
    }

    private ActivityVO toVO(Activity activity, User publisher, long memberCount, long pending, LocalDateTime now) {
        ActivityVO vo = new ActivityVO();
        BeanUtil.copyProperties(activity, vo);
        vo.setImageList(IdleService.parseJson(activity.getImages()));
        vo.setPublisherNickname(publisher == null ? "" : publisher.getNickname());
        vo.setPublisherAvatar(publisher == null ? null : publisher.getAvatar());
        vo.setMemberCount(memberCount);
        vo.setOccupiedSeats(memberCount + pending);
        // 按当前时间动态计算有效展示状态（不落库，避免时间驱动状态不一致）
        DisplayInfo info = resolveDisplayStatus(activity, now, memberCount + pending);
        vo.setDisplayStatus(info.status);
        vo.setDisplayStatusText(info.text);
        vo.setCanSignup(info.canSignup);
        vo.setSignupDisabledReason(info.reason);
        return vo;
    }

    // ---------- 活动有效状态计算（统一规则，展示与报名共用） ----------

    /**
     * 统一状态计算（展示层，不依赖定时任务）：
     * <pre>
     * 已下架(5) → 已结束(4) → 已满员(1) → 报名已截止(2) → 活动进行中(3) → 报名中(0)
     * </pre>
     * 报名截止时间优先用 signupDeadline；为空时默认用 startTime（活动开始后不可报名）。
     */
    public static DisplayInfo resolveDisplayStatus(Activity activity, LocalDateTime now, long occupiedSeats) {
        int dbStatus = activity.getStatus() == null ? Constants.ACTIVITY_SIGNING : activity.getStatus();
        // 1. 下架（数据库硬状态优先）
        if (dbStatus == Constants.ACTIVITY_OFF) {
            return new DisplayInfo(Constants.ACT_DISPLAY_OFF, "已下架", false, "活动已下架");
        }
        // 2. 数据库已结束
        if (dbStatus == Constants.ACTIVITY_ENDED) {
            return new DisplayInfo(Constants.ACT_DISPLAY_ENDED, "已结束", false, "活动已结束");
        }
        // 3. 当前时间已过结束时间
        if (activity.getEndTime() != null && !now.isBefore(activity.getEndTime())) {
            return new DisplayInfo(Constants.ACT_DISPLAY_ENDED, "已结束", false, "活动已结束");
        }
        // 4. 人数已满
        if (activity.getMaxMembers() != null && activity.getMaxMembers() > 0
                && occupiedSeats >= activity.getMaxMembers()) {
            return new DisplayInfo(Constants.ACT_DISPLAY_FULL, "已满员", false, "活动人数已满");
        }
        // 5. 报名截止
        if (activity.getSignupDeadline() != null && !now.isBefore(activity.getSignupDeadline())) {
            return new DisplayInfo(Constants.ACT_DISPLAY_DEADLINE_PASSED, "报名已截止", false, "报名已截止");
        }
        // 6. 活动已开始（signupDeadline 为空时默认截止=startTime）
        if (activity.getStartTime() != null && !now.isBefore(activity.getStartTime())) {
            return new DisplayInfo(Constants.ACT_DISPLAY_ONGOING, "活动进行中", false, "活动已开始，无法报名");
        }
        return new DisplayInfo(Constants.ACT_DISPLAY_SIGNING, "报名中", true, null);
    }

    /** 展示状态计算结果（静态内部类，便于单元测试） */
    @lombok.AllArgsConstructor
    public static class DisplayInfo {
        public final int status;
        public final String text;
        public final boolean canSignup;
        public final String reason;
    }
}
