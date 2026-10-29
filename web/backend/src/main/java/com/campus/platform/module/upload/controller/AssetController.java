package com.campus.platform.module.upload.controller;

import com.campus.platform.utils.MinioUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 对象存储公开读取代理：小程序通过后端域名加载图片，避免直接访问 MinIO 端口。
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final MinioUtils minioUtils;

    @GetMapping("/{bucket}/**")
    public ResponseEntity<byte[]> getAsset(@PathVariable String bucket,
                                           jakarta.servlet.http.HttpServletRequest request) {
        String prefix = "/api/assets/" + bucket + "/";
        String uri = request.getRequestURI();
        String objectName = uri.startsWith(prefix) ? uri.substring(prefix.length()) : "";
        MinioUtils.AssetData asset = minioUtils.readPublicAsset(bucket, objectName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .body(asset.bytes());
    }
}
