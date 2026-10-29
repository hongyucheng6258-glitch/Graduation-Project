package com.campus.platform.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * PDF 文档表（B3）。
 */
@Data
@TableName("pdf_document")
public class PdfDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String fileName;

    /** Base64 Data URI 或历史 MinIO 地址 */
    private String fileUrl;

    private Integer pageCount;

    /** PDFBox 提取全文，不回传给前端 */
    @JsonIgnore
    private String textContent;

    /** 0解析中 1成功 2失败-扫描件 */
    private Integer status;

    private LocalDateTime createTime;
}
