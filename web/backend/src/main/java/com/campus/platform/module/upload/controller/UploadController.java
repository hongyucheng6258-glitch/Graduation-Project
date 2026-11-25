package com.campus.platform.module.upload.controller;

import com.campus.platform.module.upload.service.UploadService;
import com.campus.platform.module.upload.vo.UploadVO;

import com.campus.platform.common.Result;
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
    public Result<UploadVO> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.ok(uploadService.uploadImage(UserContext.getUid(), file));
    }

    @PostMapping("/file")
    public Result<UploadVO> uploadFile(@RequestParam("file") MultipartFile file) {
        return Result.ok(uploadService.uploadFile(UserContext.getUid(), file));
    }
}
