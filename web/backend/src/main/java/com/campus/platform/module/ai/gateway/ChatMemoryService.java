package com.campus.platform.module.ai.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.module.ai.entity.AiMessage;
import com.campus.platform.module.ai.mapper.AiMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 会话记忆服务：持久化消息、按会话加载历史（供上下文拼接）。
 */
@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private final AiMessageMapper aiMessageMapper;

    /** 保存一条消息 */
    public Long saveMessage(Long sessionId, String role, String content, Integer tokens) {
        AiMessage msg = new AiMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTokens(tokens);
        aiMessageMapper.insert(msg);
        return msg.getId();
    }

    /** 查询模型上下文所需的最近 20 条消息，并按原有顺序返回。 */
    public List<AiMessage> getRecentContext(Long sessionId) {
        List<AiMessage> newestFirst = aiMessageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .select(AiMessage::getRole, AiMessage::getContent)
                .eq(AiMessage::getSessionId, sessionId)
                .orderByDesc(AiMessage::getId)
                .last("LIMIT 20"));
        List<AiMessage> context = new ArrayList<>(newestFirst);
        Collections.reverse(context);
        return context;
    }

    /** 删除会话所有消息 */
    public void deleteBySession(Long sessionId) {
        aiMessageMapper.delete(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId));
    }
}
