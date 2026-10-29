package com.campus.platform.module.partner.controller;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.R;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.partner.dto.StudyPartnerDTO;
import com.campus.platform.module.partner.entity.StudyPartner;
import com.campus.platform.module.partner.service.StudyPartnerService;
import com.campus.platform.module.partner.vo.StudyPartnerVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 学习搭子 */
@RestController
@RequestMapping("/api/partner")
@RequiredArgsConstructor
public class StudyPartnerController {

    private final StudyPartnerService partnerService;

    /** 发布搭子信息 */
    @PostMapping
    public R<StudyPartner> publish(@Valid @RequestBody StudyPartnerDTO dto) {
        return R.ok(partnerService.publish(UserContext.getUid(), dto));
    }

    /** 公开列表 */
    @GetMapping("/list")
    public R<PageResult<StudyPartnerVO>> list(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(partnerService.list(subject, keyword, pageNum, pageSize));
    }

    /** 我的发布 */
    @GetMapping("/my")
    public R<PageResult<StudyPartnerVO>> my(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(partnerService.myList(UserContext.getUid(), pageNum, pageSize));
    }

    /** AI 智能匹配 */
    @PostMapping("/match")
    public R<List<StudyPartnerVO>> match(@Valid @RequestBody StudyPartnerDTO dto) {
        return R.ok(partnerService.match(UserContext.getUid(), dto));
    }

    /** 标记已找到 */
    @PutMapping("/{id}/finish")
    public R<Void> finish(@PathVariable Long id) {
        partnerService.finish(UserContext.getUid(), id);
        return R.ok();
    }
}
