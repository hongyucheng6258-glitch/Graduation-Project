package com.campus.platform.module.ai.vo;

import com.campus.platform.module.ai.entity.AiSession;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 会话 VO：附带最后一条消息预览。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SessionVO extends AiSession {

    private String lastMessage;
}
