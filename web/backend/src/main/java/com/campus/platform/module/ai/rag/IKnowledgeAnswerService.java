package com.campus.platform.module.ai.rag;

import com.campus.platform.common.mapper.MapperQueries;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.agent.AgentModelClient;
import com.campus.platform.module.ai.agent.AgentQuota;
import com.campus.platform.module.ai.agent.IAgentPromptService;
import com.campus.platform.module.ai.agent.AgentMessageAttachments;
import com.campus.platform.module.ai.agent.IAgentService;
import com.campus.platform.module.ai.entity.AiMessage;
import com.campus.platform.module.ai.entity.AiSession;
import com.campus.platform.module.ai.mapper.AiMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.module.ai.gateway.IChatMemoryService;
import com.campus.platform.module.ai.mapper.AiSessionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * KnowledgeAnswerService 服务接口。
 */
public interface IKnowledgeAnswerService {

    InternalReply askInternal(String question);

    record Reply(String answer, List<Citation> citations) {}

    record Citation(long documentId, String fileName, int page) {}

    record InternalReply(String answer, List<InternalCitation> citations) {}

    record InternalCitation(long documentId, String fileName, int page, String version) {}

    Reply ask(Long userId, Long documentId, String question, Long sessionId);
}
