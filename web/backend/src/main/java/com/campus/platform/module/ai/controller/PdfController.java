package com.campus.platform.module.ai.controller;

import com.campus.platform.module.ai.service.IPdfService;
import com.campus.platform.module.ai.vo.PdfDocVO;

import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfController {
    private final IPdfService pdfService;

    @PostMapping("/upload")
    public Result<PdfDocVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(pdfService.upload(UserContext.getUid(), file));
    }

    @GetMapping("/{docId}")
    public Result<PdfDocVO> getDoc(@PathVariable Long docId) {
        return Result.ok(pdfService.getDoc(UserContext.getUid(), docId));
    }
}
