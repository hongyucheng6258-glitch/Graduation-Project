package com.campus.platform.module.upload.controller;

import com.campus.platform.module.upload.service.UploadService;
import com.campus.platform.module.upload.vo.UploadVO;

import com.campus.platform.common.R;
import com.campus.platform.common.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {
    private final UploadService uploadService;

    @PostMapping("/image")
    public R<UploadVO> uploadImage(@RequestParam("file") MultipartFile file) {
        return R.ok(uploadService.uploadImage(UserContext.getUid(), file));
    }

    @PostMapping("/file")
    public R<UploadVO> uploadFile(@RequestParam("file") MultipartFile file) {
        return R.ok(uploadService.uploadFile(UserContext.getUid(), file));
    }
}
