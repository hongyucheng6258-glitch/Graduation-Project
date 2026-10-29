package com.campus.platform.module.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知表（C7，轮询拉取，Q7）。
 */
@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 */
    private Long userId;

    /** system/interact/audit */
    private String type;

    private String title;

    private String content;

    /** 业务类型 idle/activity/... */
    private String bizType;

    private Long bizId;

    /** 0未读 1已读 */
    private Integer isRead;

    private LocalDateTime createTime;
}
