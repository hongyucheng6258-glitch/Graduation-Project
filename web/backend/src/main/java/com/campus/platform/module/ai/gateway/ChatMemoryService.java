package com.campus.platform.module.ai.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.module.ai.entity.AiMessage;
import com.campus.platform.module.ai.mapper.AiMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 会话记忆服务：持久化消息、按会话加载历史（供上下文拼接）。
 */
@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private final AiMessageMapper aiMessageMapper;

    /** 保存一条消息 */
    public void saveMessage(Long sessionId, String role, String content, Integer tokens) {
        AiMessage msg = new AiMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTokens(tokens);
        aiMessageMapper.insert(msg);
    }

    /** 加载会话历史消息（按时间正序，供上下文拼接） */
    public List<AiMessage> getBySession(Long sessionId) {
        return aiMessageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId)
                .orderByAsc(AiMessage::getId));
    }

    /** 删除会话所有消息 */
    public void deleteBySession(Long sessionId) {
        aiMessageMapper.delete(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId));
    }
}
