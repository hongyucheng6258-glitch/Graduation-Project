package com.campus.platform.module.activity.controller;

import com.campus.platform.module.activity.dto.ActivityPublishDTO;
import com.campus.platform.module.activity.vo.ActivityDetailVO;
import com.campus.platform.module.activity.service.ActivityService;
import com.campus.platform.module.activity.service.ActivityRecommendService;
import com.campus.platform.module.activity.vo.ActivityRecommendVO;
import com.campus.platform.module.activity.dto.MemberHandleDTO;
import com.campus.platform.module.activity.vo.ActivityVO;
import com.campus.platform.module.activity.dto.SigninDTO;
import com.campus.platform.module.activity.dto.SignupDTO;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.vo.MemberVO;

import com.campus.platform.common.BizException;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.R;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.config.SystemConfigHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {
    private final ActivityService activityService;
    private final ActivityRecommendService activityRecommendService;
    private final SystemConfigHolder systemConfigHolder;

    /** 活动 AI 智能推荐 */
    @PostMapping("/recommend")
    public R<List<ActivityRecommendVO>> recommend() {
        return R.ok(activityRecommendService.recommend(UserContext.getUid()));
    }

    @PostMapping
    public R<Activity> publish(@Valid @RequestBody ActivityPublishDTO dto) {
        if (!systemConfigHolder.isActivityPublishEnabled()) {
            throw new BizException(ResultCode.FORBIDDEN, "当前已关闭活动发布，请联系管理员");
        }
        return R.ok(activityService.publish(UserContext.getUid(), dto));
    }

    @PutMapping("/{id}")
    public R<Activity> update(@PathVariable Long id, @Valid @RequestBody ActivityPublishDTO dto) {
        return R.ok(activityService.update(UserContext.getUid(), id, dto));
    }

    @GetMapping("/list")
    public R<PageResult<ActivityVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 匿名公开接口：限制分页大小，防止一次性拉全表
        int capped = Math.min(pageSize == null ? 10 : pageSize, 50);
        return R.ok(activityService.list(keyword, category, pageNum, capped));
    }

    @GetMapping("/my")
    public R<PageResult<ActivityVO>> myPublished(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(activityService.myPublished(UserContext.getUid(), pageNum, pageSize));
    }

    @GetMapping("/my/signup")
    public R<PageResult<MemberVO>> mySignups(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(activityService.mySignups(UserContext.getUid(), pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<ActivityDetailVO> detail(@PathVariable Long id) {
        UserContext.CurrentUser current = UserContext.get();
        Long uid = current == null ? null : current.uid();
        return R.ok(activityService.detail(id, uid));
    }

    @PostMapping("/{id}/signup")
    public R<Void> signup(@PathVariable Long id, @Valid @RequestBody SignupDTO dto) {
        activityService.signup(UserContext.getUid(), id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}/signup")
    public R<Void> cancelSignup(@PathVariable Long id) {
        activityService.cancelSignup(UserContext.getUid(), id);
        return R.ok();
    }

    @GetMapping("/{id}/members")
    public R<PageResult<MemberVO>> members(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        int capped = Math.min(pageSize == null ? 10 : Math.max(1, pageSize), 100);
        return R.ok(activityService.members(UserContext.getUid(), id, Math.max(1, pageNum == null ? 1 : pageNum), capped));
    }

    @PutMapping("/member/{memberId}/handle")
    public R<Void> handleMember(@PathVariable Long memberId, @Valid @RequestBody MemberHandleDTO dto) {
        activityService.handleMember(UserContext.getUid(), memberId, dto.getApprove());
        return R.ok();
    }

    @GetMapping("/{id}/signin-qrcode")
    public R<String> signinQrCode(@PathVariable Long id) {
        return R.ok(activityService.signinQrCode(UserContext.getUid(), id));
    }

    @PostMapping("/signin")
    public R<Void> signin(@Valid @RequestBody SigninDTO dto) {
        activityService.signin(UserContext.getUid(), dto);
        return R.ok();
    }
}
