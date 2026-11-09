package com.campus.platform.module.ai.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.entity.AiMessage;
import com.campus.platform.module.ai.entity.AiSession;
import com.campus.platform.module.ai.gateway.ChatMemoryService;
import com.campus.platform.module.ai.mapper.AiMessageMapper;
import com.campus.platform.module.ai.mapper.AiSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
class AgentConversation {
    private final AiSessionMapper sessions;
    private final AiMessageMapper messages;
    private final ChatMemoryService memory;
    private final AgentMessageAttachments attachments;

    List<AiMessage> history(Long uid, Long sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        check(uid, sessionId);
        return messages.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId).orderByDesc(AiMessage::getId).last("LIMIT 12"))
                .stream().sorted(java.util.Comparator.comparing(AiMessage::getId)).toList();
    }

    String latestIdleSearch(Long sessionId) {
        if (sessionId == null) return "";
        var context = messages.selectOne(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId).eq(AiMessage::getRole, "context")
                .like(AiMessage::getContent, "\"idleSearch\":")
                .orderByDesc(AiMessage::getId).last("LIMIT 1"));
        return context == null ? "" : context.getContent();
    }

    @Transactional
    long save(Long uid, Long sessionId, String question, String answer, String context,
              List<AgentService.Citation> citations, List<String> actionIds, List<Long> idleIds, boolean idleSearch,
              List<String> idleDraftIds) {
        if (sessionId == null) {
            var session = new AiSession();
            session.setUserId(uid);
            session.setScene("agent");
            session.setTitle(question.codePoints().limit(32)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString());
            sessions.insert(session);
            sessionId = session.getId();
        } else {
            var locked = sessions.selectOne(new LambdaQueryWrapper<AiSession>().eq(AiSession::getId, sessionId)
                    .last("FOR UPDATE"));
            validate(uid, locked);
        }
        memory.saveMessage(sessionId, "user", question, null);
        long messageId = memory.saveMessage(sessionId, "assistant", answer, null);
        attachments.save(messageId, citations, actionIds, idleIds, idleSearch, idleDraftIds);
        if (!context.isEmpty()) {
            memory.saveMessage(sessionId, "context", context, null);
        }
        return sessionId;
    }

    private void check(Long uid, long id) {
        validate(uid, sessions.selectById(id));
    }

    private void validate(Long uid, AiSession session) {
        if (session == null || !uid.equals(session.getUserId()) || !"agent".equals(session.getScene())) {
            throw new BizException(ResultCode.NOT_FOUND, "未找到校园向导会话");
        }
    }
}
