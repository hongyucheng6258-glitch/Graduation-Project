package com.campus.platform.module.notice.controller;

import com.campus.platform.module.notice.service.NoticeService;
import com.campus.platform.module.notice.entity.Notice;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notice")
@RequiredArgsConstructor
public class NoticeController {
    private final NoticeService noticeService;

    @GetMapping("/list")
    public R<PageResult<Notice>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(noticeService.list(pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Notice> detail(@PathVariable Long id) {
        return R.ok(noticeService.detail(id));
    }
}
