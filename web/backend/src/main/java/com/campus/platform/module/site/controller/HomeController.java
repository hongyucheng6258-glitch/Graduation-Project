package com.campus.platform.module.site.controller;

import com.campus.platform.module.site.vo.HomeAggregateVO;

import com.campus.platform.common.Result;
import com.campus.platform.module.notice.service.INoticeService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/home")
public class HomeController {
    @Resource
    private INoticeService noticeService;

    @GetMapping("/aggregate")
    public Result<HomeAggregateVO> aggregate() {
        return Result.ok(noticeService.homeAggregate());
    }
}
