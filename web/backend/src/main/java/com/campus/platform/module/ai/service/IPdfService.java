package com.campus.platform.module.ai.service;

import com.campus.platform.module.ai.mapper.PdfDocumentMapper;
import com.campus.platform.module.ai.entity.PdfDocument;
import com.campus.platform.module.ai.vo.PdfDocVO;
import cn.hutool.core.util.StrUtil;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.utils.DataUriUtils;
import com.campus.platform.utils.PdfUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * PdfService 服务接口。
 */
public interface IPdfService {

    PdfDocVO upload(Long userId, MultipartFile file);

    PdfDocVO getDoc(Long userId, Long docId);
}
