package com.campus.platform.module.chat.service;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.upload.entity.UploadResource;
import com.campus.platform.module.upload.mapper.UploadResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UploadResourceService {
    private final UploadResourceMapper mapper;

    public UploadResource requireOwnedImage(Long ownerUserId, String url) {
        return requireOwnedImage(ownerUserId, null, url);
    }

    public UploadResource requireOwnedImage(Long ownerUserId, Long resourceId, String url) {
        UploadResource resource = resourceId == null ? mapper.findByUrl(url) : mapper.selectById(resourceId);
        if (resource == null || !ownerUserId.equals(resource.getOwnerUserId()) ||
                !"image".equals(resource.getResourceType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "图片资源不存在或不属于当前用户");
        }
        if (resourceId != null && !url.equals(resource.getResourceUrl())) {
            throw new BizException(ResultCode.BAD_REQUEST, "图片资源内容不匹配");
        }
        if (resource.getBizType() != null) {
            throw new BizException(ResultCode.BAD_REQUEST, "图片资源已被使用");
        }
        return resource;
    }

    public void bindChatMessage(UploadResource resource, Long ownerUserId, Long messageId) {
        if (resource == null) return;
        if (resource.getBizType() == null && mapper.bindIfUnbound(resource.getId(), ownerUserId, "chat_message", messageId) != 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "图片资源已被使用");
        }
    }
}
