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
import com.campus.platform.common.R;
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
    public R<List<LostMatchVO>> match(@Valid @RequestBody LostMatchDTO dto) {
        return R.ok(lostMatchService.match(UserContext.getUid(), dto.getTitle(), dto.getDescription()));
    }

    @PostMapping
    public R<LostFound> publish(@Valid @RequestBody LostFoundPublishDTO dto) {
        return R.ok(lostFoundService.publish(UserContext.getUid(), dto));
    }

    @GetMapping("/list")
    public R<PageResult<LostFoundVO>> list(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(lostFoundService.list(type, keyword, pageNum, pageSize));
    }

    @GetMapping("/my")
    public R<PageResult<LostFoundVO>> my(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(lostFoundService.myList(UserContext.getUid(), pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<LostFoundVO> detail(@PathVariable Long id) {
        UserContext.CurrentUser current = UserContext.get();
        Long uid = current == null ? null : current.uid();
        return R.ok(lostFoundService.detail(id, uid));
    }

    @PutMapping("/{id}")
    public R<LostFound> update(@PathVariable Long id, @Valid @RequestBody LostFoundPublishDTO dto) {
        return R.ok(lostFoundService.update(UserContext.getUid(), id, dto));
    }

    @PutMapping("/{id}/finish")
    public R<Void> finish(@PathVariable Long id) {
        lostFoundService.finish(UserContext.getUid(), id);
        return R.ok();
    }

    @PostMapping("/{id}/claim")
    public R<Void> claim(@PathVariable Long id, @Valid @RequestBody ClaimDTO dto) {
        lostFoundService.claim(UserContext.getUid(), id, dto);
        return R.ok();
    }

    @GetMapping("/{id}/my-claim")
    public R<ClaimVO> myClaim(@PathVariable Long id) {
        return R.ok(lostFoundService.myClaim(UserContext.getUid(), id));
    }

    @GetMapping("/{id}/claims")
    public R<PageResult<ClaimVO>> claims(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        int capped = Math.min(pageSize == null ? 10 : Math.max(1, pageSize), 100);
        return R.ok(lostFoundService.claims(UserContext.getUid(), id,
                Math.max(1, pageNum == null ? 1 : pageNum), capped));
    }

    @PutMapping("/claim/{claimId}/handle")
    public R<Void> handleClaim(@PathVariable Long claimId, @Valid @RequestBody ClaimHandleDTO dto) {
        lostFoundService.handleClaim(UserContext.getUid(), claimId, dto.getAccept());
        return R.ok();
    }

    @PutMapping("/claim/{claimId}/confirm")
    public R<Void> confirmReturn(@PathVariable Long claimId) {
        lostFoundService.confirmReturn(UserContext.getUid(), claimId);
        return R.ok();
    }
}
