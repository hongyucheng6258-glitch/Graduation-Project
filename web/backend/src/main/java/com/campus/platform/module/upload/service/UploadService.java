package com.campus.platform.module.upload.service;

import com.campus.platform.module.upload.mapper.UploadResourceMapper;
import com.campus.platform.module.upload.entity.UploadResource;
import com.campus.platform.module.upload.vo.UploadVO;

import cn.hutool.core.util.StrUtil;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.utils.DataUriUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 上传服务（A4）：图片 ≤3MB，文件（PDF）≤20MB，所有新资源保存到数据库。
 */
@Service
@RequiredArgsConstructor
public class UploadService {

    private static final long IMAGE_MAX_SIZE = 3 * 1024 * 1024;
    // base64 后约 4MB，MEDIUMTEXT(16MB) 足够且小程序渲染稳定；超过 3MB 的照片请压缩后上传
    private static final long FILE_MAX_SIZE = 20 * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/jpg");

    private final DataUriUtils dataUriUtils;
    private final UploadResourceMapper uploadResourceMapper;

    public UploadVO uploadImage(Long ownerUserId, MultipartFile file) {
        checkFile(file, IMAGE_MAX_SIZE, "图片不能超过3MB");
        String contentType = StrUtil.nullToEmpty(file.getContentType()).toLowerCase();
        if (!IMAGE_TYPES.contains(contentType)) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅支持 jpg/png/gif/webp 图片");
        }
        String url = dataUriUtils.toDataUri(file);
        UploadResource resource = record(ownerUserId, url, "image", contentType, file.getSize());
        return new UploadVO(resource.getId(), url);
    }

    public UploadVO uploadFile(Long ownerUserId, MultipartFile file) {
        checkFile(file, FILE_MAX_SIZE, "文件不能超过20MB");
        String url = dataUriUtils.toDataUri(file);
        UploadResource resource = record(ownerUserId, url, "file", StrUtil.nullToEmpty(file.getContentType()), file.getSize());
        return new UploadVO(resource.getId(), url);
    }

    private UploadResource record(Long ownerUserId, String url, String type, String contentType, long size) {
        UploadResource resource = new UploadResource();
        resource.setOwnerUserId(ownerUserId);
        resource.setResourceUrl(url);
        resource.setResourceType(type);
        resource.setContentType(contentType);
        resource.setFileSize(size);
        uploadResourceMapper.insert(resource);
        return resource;
    }

    private void checkFile(MultipartFile file, long maxSize, String sizeMsg) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > maxSize) {
            throw new BizException(ResultCode.BAD_REQUEST, sizeMsg);
        }
    }
}
