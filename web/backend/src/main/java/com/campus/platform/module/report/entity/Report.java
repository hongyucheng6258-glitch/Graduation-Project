package com.campus.platform.module.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报表（D3）。
 */
@Data
@TableName("report")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 举报人 */
    private Long reporterId;

    /** idle/activity/lostfound/post/comment */
    private String targetType;

    private Long targetId;

    /** 举报类型 */
    private String reasonType;

    private String reason;

    /** 0待处理 1已处理 */
    private Integer status;

    private String handleResult;

    /** 处理管理员 */
    private Long handlerId;

    private LocalDateTime handleTime;

    private LocalDateTime createTime;
}
