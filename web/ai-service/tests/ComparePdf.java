package com.campus.platform.module.ai.rag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * 用项目真实的 PdfChunker（PDFBox）输出切片，供 tests/compare_pdf.py 与 Python 实现比对。
 *
 * 编译运行见 tests/compare_pdf.py；本文件不参与 Maven 构建。
 */
public class ComparePdf {
    public static void main(String[] args) throws Exception {
        byte[] content = Files.readAllBytes(Path.of(args[0]));
        int chunkSize = Integer.parseInt(args[1]);
        List<PdfChunker.Part> parts = new PdfChunker(chunkSize).split(content);
        for (PdfChunker.Part part : parts) {
            System.out.println(part.page() + "\t" + Base64.getEncoder().encodeToString(part.text().getBytes("UTF-8")));
        }
    }
}
