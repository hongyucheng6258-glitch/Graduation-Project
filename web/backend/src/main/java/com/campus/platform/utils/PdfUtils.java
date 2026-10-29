package com.campus.platform.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * PDF 工具：基于 PDFBox 提取文本型 PDF 全文。
 * 扫描件（图片型）提取结果为空，业务层据此标记 status=2（Q8 已拍板不做 OCR）。
 */
@Slf4j
public class PdfUtils {

    /**
     * 提取 PDF 文本与页数。
     *
     * @param file 上传的 PDF
     * @return [页数, 文本内容]
     */
    public static PdfExtractResult extract(MultipartFile file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            int pages = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            return new PdfExtractResult(pages, text == null ? "" : text.trim());
        }
    }

    /**
     * 截取与问题相关的文档片段，避免超 token（架构设计 1.1 难点5）：
     * 取「文档头部 + 含问题关键词的片段」，总长度不超过 maxLen。
     *
     * @param fullText PDF 全文
     * @param question 用户问题
     * @param maxLen   最大截取长度
     */
    public static String pickRelevant(String fullText, String question, int maxLen) {
        if (fullText == null) {
            return "";
        }
        if (fullText.length() <= maxLen) {
            return fullText;
        }
        StringBuilder sb = new StringBuilder();
        // 1. 文档头部（通常是目录/概述）
        int headLen = maxLen / 3;
        sb.append(fullText, 0, headLen).append("\n...\n");
        // 2. 关键词命中片段
        String[] keywords = question.replaceAll("[？?，,。.!！\\s]+", " ")
                .split(" ");
        int remain = maxLen - sb.length();
        for (String kw : keywords) {
            if (kw.length() < 2 || remain <= 0) {
                continue;
            }
            int idx = fullText.indexOf(kw, headLen);
            while (idx >= 0 && remain > 0) {
                int from = Math.max(idx - 200, headLen);
                int to = Math.min(idx + 400, fullText.length());
                sb.append(fullText, from, to).append("\n...\n");
                remain -= (to - from);
                idx = fullText.indexOf(kw, idx + kw.length());
            }
        }
        return sb.length() > maxLen ? sb.substring(0, maxLen) : sb.toString();
    }

    /** PDF 提取结果：页数 + 文本 */
    public record PdfExtractResult(int pageCount, String text) {
    }
}
