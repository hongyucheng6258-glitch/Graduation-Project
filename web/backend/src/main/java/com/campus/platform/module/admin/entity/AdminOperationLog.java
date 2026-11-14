package com.campus.platform.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_operation_log")
public class AdminOperationLog {
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long adminId;
    private String adminUsername;
    private String module;
    private String action;
    private String targetType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;
    private String source;
    private boolean succeeded;
    private Integer resultCode;
    private Long durationMs;
    private LocalDateTime createTime;
}
