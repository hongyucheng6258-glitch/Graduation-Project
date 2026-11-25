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
 * UploadService 服务接口。
 */
public interface IUploadService {

    UploadVO uploadImage(Long ownerUserId, MultipartFile file);

    UploadVO uploadFile(Long ownerUserId, MultipartFile file);
}
