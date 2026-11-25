package com.campus.platform.module.lostfound.controller;

import com.campus.platform.module.lostfound.dto.ClaimDTO;
import com.campus.platform.module.lostfound.dto.ClaimHandleDTO;
import com.campus.platform.module.lostfound.dto.LostFoundPublishDTO;
import com.campus.platform.module.lostfound.dto.LostMatchDTO;
import com.campus.platform.module.lostfound.service.LostFoundService;
import com.campus.platform.module.lostfound.service.LostMatchService;
import com.campus.platform.module.lostfound.vo.ClaimVO;
import com.campus.platform.module.lostfound.vo.LostFoundVO;
import com.campus.platform.module.lostfound.vo.LostMatchVO;
import com.campus.platform.module.lostfound.entity.LostFound;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lostfound")
@RequiredArgsConstructor
public class LostFoundController {
    private final LostFoundService lostFoundService;
    private final LostMatchService lostMatchService;

    /** 失物 AI 智能匹配：根据丢失物品信息匹配库中拾到记录 */
    @PostMapping("/match")
    public Result<List<LostMatchVO>> match(@Valid @RequestBody LostMatchDTO dto) {
        return Result.ok(lostMatchService.match(UserContext.getUid(), dto.getTitle(), dto.getDescription()));
    }

    @PostMapping
    public Result<LostFound> publish(@Valid @RequestBody LostFoundPublishDTO dto) {
        return Result.ok(lostFoundService.publish(UserContext.getUid(), dto));
    }

    @GetMapping("/list")
    public Result<PageResult<LostFoundVO>> list(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(lostFoundService.list(type, keyword, pageNum, pageSize));
    }

    @GetMapping("/my")
    public Result<PageResult<LostFoundVO>> my(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(lostFoundService.myList(UserContext.getUid(), pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<LostFoundVO> detail(@PathVariable Long id) {
        UserContext.CurrentUser current = UserContext.get();
        Long uid = current == null ? null : current.uid();
        return Result.ok(lostFoundService.detail(id, uid));
    }

    @PutMapping("/{id}")
    public Result<LostFound> update(@PathVariable Long id, @Valid @RequestBody LostFoundPublishDTO dto) {
        return Result.ok(lostFoundService.update(UserContext.getUid(), id, dto));
    }

    @PutMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        lostFoundService.finish(UserContext.getUid(), id);
        return Result.ok();
    }

    @PostMapping("/{id}/claim")
    public Result<Void> claim(@PathVariable Long id, @Valid @RequestBody ClaimDTO dto) {
        lostFoundService.claim(UserContext.getUid(), id, dto);
        return Result.ok();
    }

    @GetMapping("/{id}/my-claim")
    public Result<ClaimVO> myClaim(@PathVariable Long id) {
        return Result.ok(lostFoundService.myClaim(UserContext.getUid(), id));
    }

    @GetMapping("/{id}/claims")
    public Result<PageResult<ClaimVO>> claims(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        int capped = Math.min(pageSize == null ? 10 : Math.max(1, pageSize), 100);
        return Result.ok(lostFoundService.claims(UserContext.getUid(), id,
                Math.max(1, pageNum == null ? 1 : pageNum), capped));
    }

    @PutMapping("/claim/{claimId}/handle")
    public Result<Void> handleClaim(@PathVariable Long claimId, @Valid @RequestBody ClaimHandleDTO dto) {
        lostFoundService.handleClaim(UserContext.getUid(), claimId, dto.getAccept());
        return Result.ok();
    }

    @PutMapping("/claim/{claimId}/confirm")
    public Result<Void> confirmReturn(@PathVariable Long claimId) {
        lostFoundService.confirmReturn(UserContext.getUid(), claimId);
        return Result.ok();
    }
}
