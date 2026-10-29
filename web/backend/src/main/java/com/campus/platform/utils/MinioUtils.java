package com.campus.platform.utils;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.config.MinioConfig;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * MinIO 工具：保留历史对象上传与读取能力。
 * 新上传入口默认使用数据库 Data URI；本类的对象存储方法用于历史兼容和明确指定 MinIO 的场景。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioUtils {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * 上传文件到 MinIO。
     *
     * @param file   上传文件
     * @param dir    业务目录（如 idle/activity/pdf）
     * @return 可访问的文件 URL
     */
    public String upload(MultipartFile file, String dir) {
        try {
            ensureBucket();
            // 对象名：目录/日期/UUID.扩展名
            String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
            String ext = original.contains(".")
                    ? original.substring(original.lastIndexOf('.')) : "";
            String objectName = dir + "/" + DateUtil.format(DateUtil.date(), "yyyyMMdd")
                    + "/" + IdUtil.fastSimpleUUID() + ext;
            try (InputStream in = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(objectName)
                        .stream(in, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }
            // 返回后端代理相对路径：Web 端同源直接显示，小程序端由响应归一化拼上主机。
            // 不返回 MinIO 原始地址（localhost:9000 在真机上不可达）。
            return "/api/assets/" + minioConfig.getBucket() + "/" + objectName;
        } catch (Exception e) {
            log.error("MinIO 上传失败", e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件上传失败，请稍后重试");
        }
    }

    /**
     * 图片直接以 base64 data URI 返回（存数据库，不依赖 MinIO 可达性/端口/域名）。
     * 毕业设计演示环境采用该方案：前端（Web/小程序）拿到 data:image/...;base64,... 直接显示。
     */
    public String uploadImageAsDataUri(MultipartFile file, String dir) {
        String contentType = StrUtil.nullToEmpty(file.getContentType()).toLowerCase();
        if (contentType.isBlank()) contentType = "image/jpeg";
        try (InputStream in = file.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            String base64 = Base64.getEncoder().encodeToString(out.toByteArray());
            return "data:" + contentType + ";base64," + base64;
        } catch (IOException e) {
            log.error("图片转 base64 失败", e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "图片上传失败，请稍后重试");
        }
    }

    /** 通过后端读取对象，供小程序使用同源图片地址。 */
    public AssetData readPublicAsset(String bucket, String objectName) {
        if (!minioConfig.getBucket().equals(bucket) || objectName == null || objectName.isBlank()
                || objectName.contains("..") || objectName.startsWith("/")) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        try (GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
             InputStream input = response;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            String contentType = response.headers().get("Content-Type");
            return new AssetData(output.toByteArray(), contentType == null ? "application/octet-stream" : contentType);
        } catch (Exception e) {
            log.warn("读取 MinIO 资源失败: {}/{}", bucket, objectName, e);
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
    }

    public record AssetData(byte[] bytes, String contentType) {}

    /** 确保 bucket 存在并设置匿名读策略（供图片回显，架构设计第10章假设3） */
    private void ensureBucket() throws Exception {
        String bucket = minioConfig.getBucket();
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        // 每次都确保匿名读策略有效，兼容已存在但未配置公开读权限的 bucket
        String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"AWS": ["*"]},
                    "Action": ["s3:GetObject"],
                    "Resource": ["arn:aws:s3:::%s/*"]
                  }]
                }
                """.formatted(bucket);
        minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                .bucket(bucket).config(policy).build());
    }

    /** 按文件URL删除（用于删除记录时清理，可选） */
    public String getBucket() {
        return minioConfig.getBucket();
    }
}
