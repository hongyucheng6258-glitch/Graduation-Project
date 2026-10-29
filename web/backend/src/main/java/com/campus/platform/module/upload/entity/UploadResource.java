package com.campus.platform.module.upload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("upload_resource")
public class UploadResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private String resourceUrl;
    private String resourceType;
    private String contentType;
    private Long fileSize;
    private String bizType;
    private Long bizId;
    private LocalDateTime createTime;
}
