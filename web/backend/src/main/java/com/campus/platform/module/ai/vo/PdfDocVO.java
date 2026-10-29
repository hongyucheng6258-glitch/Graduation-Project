package com.campus.platform.module.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * PDF 上传解析响应。
 */
@Data
@AllArgsConstructor
public class PdfDocVO {

    private Long docId;

    private String fileName;

    private Integer pageCount;

    /** 0解析中 1成功 2失败-扫描件 */
    private Integer status;
}
