package com.campus.platform.module.ai.controller;

import com.campus.platform.module.ai.service.PdfService;
import com.campus.platform.module.ai.vo.PdfDocVO;

import com.campus.platform.common.R;
import com.campus.platform.common.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfController {
    private final PdfService pdfService;

    @PostMapping("/upload")
    public R<PdfDocVO> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(pdfService.upload(UserContext.getUid(), file));
    }

    @GetMapping("/{docId}")
    public R<PdfDocVO> getDoc(@PathVariable Long docId) {
        return R.ok(pdfService.getDoc(UserContext.getUid(), docId));
    }
}
