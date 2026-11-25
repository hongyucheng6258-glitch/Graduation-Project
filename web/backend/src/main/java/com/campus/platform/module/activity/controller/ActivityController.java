package com.campus.platform.module.activity.controller;

import com.campus.platform.module.activity.dto.ActivityPublishDTO;
import com.campus.platform.module.activity.vo.ActivityDetailVO;
import com.campus.platform.module.activity.service.ActivityService;
import com.campus.platform.module.activity.service.ActivityScheduleService;
import com.campus.platform.module.activity.service.ActivityWaitlist;
import com.campus.platform.module.activity.service.ActivityRecommendService;
import com.campus.platform.module.activity.vo.ActivityRecommendVO;
import com.campus.platform.module.activity.dto.MemberHandleDTO;
import com.campus.platform.module.activity.vo.ActivityVO;
import com.campus.platform.module.activity.dto.SigninDTO;
import com.campus.platform.module.activity.dto.SignupDTO;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.vo.MemberVO;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
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
    private final ActivityScheduleService activityScheduleService;
    private final ActivityWaitlist waitlist;

    /** 活动 AI 智能推荐 */
    @PostMapping("/recommend")
    public Result<List<ActivityRecommendVO>> recommend() {
        return Result.ok(activityRecommendService.recommend(UserContext.getUid()));
    }

    @PostMapping
    public Result<Activity> publish(@Valid @RequestBody ActivityPublishDTO dto) {
        return Result.ok(activityService.publish(UserContext.getUid(), dto));
    }

    @PutMapping("/{id}")
    public Result<Activity> update(@PathVariable Long id, @Valid @RequestBody ActivityPublishDTO dto) {
        return Result.ok(activityService.update(UserContext.getUid(), id, dto));
    }

    @GetMapping("/list")
    public Result<PageResult<ActivityVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 匿名公开接口：限制分页大小，防止一次性拉全表
        int capped = Math.min(pageSize == null ? 10 : pageSize, 50);
        return Result.ok(activityService.list(keyword, category, pageNum, capped));
    }

    @GetMapping("/my")
    public Result<PageResult<ActivityVO>> myPublished(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(activityService.myPublished(UserContext.getUid(), pageNum, pageSize));
    }

    @GetMapping("/my/signup")
    public Result<PageResult<MemberVO>> mySignups(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(activityService.mySignups(UserContext.getUid(), pageNum, pageSize));
    }

    @GetMapping("/my/schedule")
    public Result<ActivityScheduleService.Schedule> mySchedule() {
        return Result.ok(activityScheduleService.schedule(UserContext.getUid()));
    }

    @GetMapping("/{id}/signup-conflicts")
    public Result<ActivityScheduleService.ConflictCheck> signupConflicts(@PathVariable Long id) {
        return Result.ok(activityScheduleService.conflicts(UserContext.getUid(), id));
    }

    @GetMapping("/{id}")
    public Result<ActivityDetailVO> detail(@PathVariable Long id) {
        UserContext.CurrentUser current = UserContext.get();
        Long uid = current == null ? null : current.uid();
        return Result.ok(activityService.detail(id, uid));
    }

    @PostMapping("/{id}/signup")
    public Result<Void> signup(@PathVariable Long id, @Valid @RequestBody SignupDTO dto) {
        activityService.signup(UserContext.getUid(), id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}/signup")
    public Result<Void> cancelSignup(@PathVariable Long id) {
        activityService.cancelSignup(UserContext.getUid(), id);
        return Result.ok();
    }

    @GetMapping("/my/waitlist")
    public Result<List<ActivityWaitlist.Entry>> myWaitlist() {
        return Result.ok(waitlist.mine(UserContext.getUid()));
    }

    @GetMapping("/{id}/waitlist")
    public Result<ActivityWaitlist.View> waitlist(@PathVariable Long id) {
        return Result.ok(waitlist.view(UserContext.getUid(), id));
    }

    @PostMapping("/{id}/waitlist")
    public Result<ActivityWaitlist.Entry> joinWaitlist(@PathVariable Long id) {
        return Result.ok(waitlist.join(UserContext.getUid(), id));
    }

    @DeleteMapping("/{id}/waitlist/{entryId}")
    public Result<Void> leaveWaitlist(@PathVariable Long id, @PathVariable Long entryId) {
        waitlist.leave(UserContext.getUid(), id, entryId);
        return Result.ok();
    }

    @PostMapping("/{id}/waitlist/{entryId}/accept")
    public Result<Void> acceptWaitlist(@PathVariable Long id, @PathVariable Long entryId, @Valid @RequestBody SignupDTO dto) {
        activityService.acceptWaitlist(UserContext.getUid(), id, entryId, dto);
        return Result.ok();
    }

    @GetMapping("/{id}/members")
    public Result<PageResult<MemberVO>> members(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        int capped = Math.min(pageSize == null ? 10 : Math.max(1, pageSize), 100);
        return Result.ok(activityService.members(UserContext.getUid(), id, Math.max(1, pageNum == null ? 1 : pageNum), capped));
    }

    @PutMapping("/member/{memberId}/handle")
    public Result<Void> handleMember(@PathVariable Long memberId, @Valid @RequestBody MemberHandleDTO dto) {
        activityService.handleMember(UserContext.getUid(), memberId, dto.getApprove());
        return Result.ok();
    }

    @GetMapping("/{id}/signin-qrcode")
    public Result<String> signinQrCode(@PathVariable Long id) {
        return Result.ok(activityService.signinQrCode(UserContext.getUid(), id));
    }

    @PostMapping("/signin")
    public Result<Void> signin(@Valid @RequestBody SigninDTO dto) {
        activityService.signin(UserContext.getUid(), dto);
        return Result.ok();
    }
}
