package com.campus.platform.module.site.controller;

import com.campus.platform.module.site.vo.HomeAggregateVO;

import com.campus.platform.common.R;
import com.campus.platform.module.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {
    private final NoticeService noticeService;

    @GetMapping("/aggregate")
    public R<HomeAggregateVO> aggregate() {
        return R.ok(noticeService.homeAggregate());
    }
}
