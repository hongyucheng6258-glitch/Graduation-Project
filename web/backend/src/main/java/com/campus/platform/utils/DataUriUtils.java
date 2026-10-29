package com.campus.platform.utils;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

/** 将上传文件编码为可直接使用的 Data URI。 */
@Component
public class DataUriUtils {

    public String toDataUri(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        String contentType = StrUtil.nullToEmpty(file.getContentType()).trim().toLowerCase();
        if (contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        try {
            return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件编码失败，请稍后重试");
        }
    }
}
