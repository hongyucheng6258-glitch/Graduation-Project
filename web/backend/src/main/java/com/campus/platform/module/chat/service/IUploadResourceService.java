package com.campus.platform.module.chat.service;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.upload.entity.UploadResource;
import com.campus.platform.module.upload.mapper.UploadResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * UploadResourceService 服务接口。
 */
public interface IUploadResourceService {

    UploadResource requireOwnedImage(Long ownerUserId, String url);

    UploadResource requireOwnedImage(Long ownerUserId, Long resourceId, String url);

    void bindChatMessage(UploadResource resource, Long ownerUserId, Long messageId);
}
