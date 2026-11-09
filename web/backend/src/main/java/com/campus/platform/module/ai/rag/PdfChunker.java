package com.campus.platform.module.ai.rag;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class PdfChunker {
    static final int DEFAULT_SIZE = 640;
    private static final int OVERLAP = 80;
    private static final int MAX_PARTS = 500;
    private final int chunkSize;

    PdfChunker() {
        this(DEFAULT_SIZE);
    }

    PdfChunker(int chunkSize) {
        if (chunkSize < 160 || chunkSize > 640) {
            throw new IllegalArgumentException("PDF 索引分段长度须在 160 至 640 字符之间");
        }
        this.chunkSize = chunkSize;
    }

    record Part(int page, String text) {}

    List<Part> split(byte[] bytes) throws IOException {
        try (var pdf = Loader.loadPDF(bytes)) {
            if (pdf.getNumberOfPages() > 200) {
                throw new BizException(ResultCode.BAD_REQUEST, "知识库 PDF 最多支持 200 页");
            }
            List<Part> parts = new ArrayList<>();
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdf).replaceAll("\\s+", " ").strip();
                for (int start = 0; start < text.length();) {
                    int end = Math.min(start + chunkSize, text.length());
                    if (end < text.length()) {
                        int boundary = Math.max(text.lastIndexOf('。', end - 1), text.lastIndexOf(' ', end - 1));
                        if (boundary > start + chunkSize / 2) {
                            end = boundary + 1;
                        }
                        if (Character.isHighSurrogate(text.charAt(end - 1))) {
                            end--;
                        }
                    }
                    parts.add(new Part(page, text.substring(start, end).strip()));
                    if (parts.size() > MAX_PARTS) {
                        throw new BizException(ResultCode.BAD_REQUEST, "PDF 可索引文本过长，请拆分后上传");
                    }
                    if (end == text.length()) {
                        break;
                    }
                    start = end - OVERLAP;
                    if (Character.isLowSurrogate(text.charAt(start))) {
                        start++;
                    }
                }
            }
            if (parts.isEmpty()) {
                throw new BizException(ResultCode.PDF_SCANNED);
            }
            return parts;
        }
    }
}
