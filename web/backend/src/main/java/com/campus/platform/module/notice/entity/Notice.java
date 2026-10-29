package com.campus.platform.module.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告表（C5/D4）。
 */
@Data
@TableName("notice")
public class Notice {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布管理员 */
    private Long adminId;

    private String title;

    /** Markdown 内容 */
    private String content;

    private String cover;

    /** 0草稿 1已发布 2已下线 */
    private Integer status;

    private LocalDateTime publishTime;

    private LocalDateTime createTime;
}
