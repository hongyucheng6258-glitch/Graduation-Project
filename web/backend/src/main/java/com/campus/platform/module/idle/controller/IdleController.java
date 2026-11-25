package com.campus.platform.module.idle.controller;

import com.campus.platform.module.idle.dto.AppointHandleDTO;
import com.campus.platform.module.idle.dto.IdleEstimateDTO;
import com.campus.platform.module.idle.dto.IdleHandoffDTO;
import com.campus.platform.module.idle.service.IdleHandoffService;
import com.campus.platform.module.idle.entity.IdleAppointment;
import com.campus.platform.module.idle.dto.IdlePublishDTO;
import com.campus.platform.module.idle.vo.AppointmentVO;
import com.campus.platform.module.idle.vo.IdleDetailVO;
import com.campus.platform.module.idle.service.IdleService;
import com.campus.platform.module.idle.service.IdleEstimateService;
import com.campus.platform.module.idle.vo.IdleEstimateVO;
import com.campus.platform.module.idle.dto.AppointDTO;
import com.campus.platform.module.idle.vo.IdleItemVO;
import com.campus.platform.module.idle.entity.IdleItem;

import com.campus.platform.common.BizException;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.Result;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.config.SystemConfigHolder;
import com.campus.platform.module.post.dto.ReviewDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/idle")
@RequiredArgsConstructor
public class IdleController {
    private final IdleService idleService;
    private final IdleEstimateService idleEstimateService;
    private final SystemConfigHolder systemConfigHolder;
    private final IdleHandoffService handoffs;

    /** 闲置 AI 智能估价 */
    @PostMapping("/estimate")
    public Result<IdleEstimateVO> estimate(@Valid @RequestBody IdleEstimateDTO dto) {
        return Result.ok(idleEstimateService.estimate(UserContext.getUid(),
                dto.getTitle(), dto.getDescription(), dto.getCategory(), dto.getExpectItem()));
    }

    @PostMapping
    public Result<IdleItem> publish(@Valid @RequestBody IdlePublishDTO dto) {
        if (!systemConfigHolder.isIdlePublishEnabled()) {
            throw new BizException(ResultCode.FORBIDDEN, "当前已关闭闲置发布，请联系管理员");
        }
        return Result.ok(idleService.publish(UserContext.getUid(), dto));
    }

    @GetMapping("/list")
    public Result<PageResult<IdleItemVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(idleService.list(keyword, category, pageNum, pageSize));
    }

    @GetMapping("/my")
    public Result<PageResult<IdleItemVO>> my(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(idleService.myList(UserContext.getUid(), pageNum, pageSize));
    }

    @GetMapping("/appoint/my")
    public Result<PageResult<AppointmentVO>> myAppointments(
            @RequestParam(defaultValue = "buyer") String role,
            @RequestParam(required = false) Long appointmentId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(idleService.myAppointments(UserContext.getUid(), role, pageNum, pageSize, appointmentId));
    }

    @GetMapping("/{id}")
    public Result<IdleDetailVO> detail(@PathVariable Long id) {
        UserContext.CurrentUser current = UserContext.get();
        Long uid = current == null ? null : current.uid();
        return Result.ok(idleService.detail(id, uid));
    }

    @PutMapping("/{id}")
    public Result<IdleItem> update(@PathVariable Long id, @Valid @RequestBody IdlePublishDTO dto) {
        return Result.ok(idleService.update(UserContext.getUid(), id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> offline(@PathVariable Long id) {
        idleService.offline(UserContext.getUid(), id);
        return Result.ok();
    }
    @PutMapping("/{id}/relist")
    public Result<Void> relist(@PathVariable Long id) {
        idleService.relist(UserContext.getUid(), id);
        return Result.ok();
    }

    @PostMapping("/{id}/appoint")
    public Result<IdleAppointment> appoint(@PathVariable Long id, @Valid @RequestBody AppointDTO dto) {
        return Result.ok(idleService.appoint(UserContext.getUid(), id, dto));
    }

    @PutMapping("/appoint/{id}/handle")
    public Result<Void> handleAppoint(@PathVariable Long id, @Valid @RequestBody AppointHandleDTO dto) {
        idleService.handleAppoint(UserContext.getUid(), id, dto.getAccept());
        return Result.ok();
    }

    @GetMapping("/appoint/{id}/handoff")
    public Result<IdleHandoffService.Plan> handoff(@PathVariable Long id) {
        return Result.ok(handoffs.view(UserContext.getUid(), id));
    }

    @PutMapping("/appoint/{id}/handoff")
    public Result<IdleHandoffService.Plan> proposeHandoff(@PathVariable Long id, @Valid @RequestBody IdleHandoffDTO dto) {
        return Result.ok(handoffs.propose(UserContext.getUid(), id, dto.version(), dto.meetingAt(), dto.location()));
    }

    @PutMapping("/appoint/{id}/handoff/confirm")
    public Result<IdleHandoffService.Plan> confirmHandoff(@PathVariable Long id, @Valid @RequestBody IdleHandoffDTO.Confirmation dto) {
        return Result.ok(handoffs.confirm(UserContext.getUid(), id, dto.version()));
    }

    @PutMapping("/appoint/{id}/cancel")
    public Result<Void> cancelAppoint(@PathVariable Long id) {
        idleService.cancelAppoint(UserContext.getUid(), id);
        return Result.ok();
    }

    @PutMapping("/appoint/{id}/finish")
    public Result<Void> finishAppoint(@PathVariable Long id) {
        idleService.finishAppoint(UserContext.getUid(), id);
        return Result.ok();
    }

    @PostMapping("/appoint/{id}/review")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewDTO dto) {
        idleService.review(UserContext.getUid(), id, dto);
        return Result.ok();
    }
}
