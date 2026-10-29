package com.campus.platform.module.message.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 未读消息数响应（前端 30s 轮询角标）。
 */
@Data
@AllArgsConstructor
public class UnreadCountVO {

    private Long count;
}
