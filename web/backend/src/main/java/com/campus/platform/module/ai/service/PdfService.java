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
 * PDF 解析服务（B3）：上传 → 数据库 Data URI → PDFBox 提取 → 入库。
 * 扫描件（提取文本为空）标记 status=2 并提示 1004。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {

    private static final long PDF_MAX_SIZE = 20 * 1024 * 1024;

    private final PdfDocumentMapper pdfDocumentMapper;
    private final DataUriUtils dataUriUtils;

    /**
     * 上传并解析 PDF。
     */
    public PdfDocVO upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        String name = StrUtil.nullToEmpty(file.getOriginalFilename());
        if (!name.toLowerCase().endsWith(".pdf")) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅支持 PDF 文件");
        }
        if (file.getSize() > PDF_MAX_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST, "PDF 不能超过20MB");
        }
        // 1. 将新 PDF 编码为数据库可直接保存的 Data URI
        String url = dataUriUtils.toDataUri(file);
        // 2. PDFBox 解析
        PdfDocument doc = new PdfDocument();
        doc.setUserId(userId);
        doc.setFileName(name);
        doc.setFileUrl(url);
        try {
            PdfUtils.PdfExtractResult result = PdfUtils.extract(file);
            doc.setPageCount(result.pageCount());
            if (StrUtil.isBlank(result.text())) {
                // 扫描件：文本提取为空
                doc.setStatus(2);
                pdfDocumentMapper.insert(doc);
                throw new BizException(ResultCode.PDF_SCANNED);
            }
            doc.setTextContent(result.text());
            doc.setStatus(1);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF 解析失败", e);
            doc.setStatus(2);
            pdfDocumentMapper.insert(doc);
            throw new BizException(ResultCode.BAD_REQUEST, "PDF 解析失败，文件可能已损坏");
        }
        pdfDocumentMapper.insert(doc);
        return new PdfDocVO(doc.getId(), doc.getFileName(), doc.getPageCount(), doc.getStatus());
    }

    /** 查询解析状态与摘要 */
    public PdfDocVO getDoc(Long userId, Long docId) {
        PdfDocument doc = pdfDocumentMapper.selectById(docId);
        if (doc == null || !doc.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "文档不存在");
        }
        return new PdfDocVO(doc.getId(), doc.getFileName(), doc.getPageCount(), doc.getStatus());
    }
}
