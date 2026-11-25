package com.campus.platform.module.ai.adminassistant;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.admin.service.IStatsService;
import com.campus.platform.module.ai.agent.AgentModelClient;
import com.campus.platform.module.ai.agent.AgentTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AdminAssistantService 服务接口。
 */
public interface IAdminAssistantService {

    Reply ask(Long adminId, String sessionId, String question);

    List<TurnView> history(Long adminId, String sessionId);

    record Task(String key, long count) {}

    record Turn(String question, String answer, String tool, Instant queriedAt, List<Task> tasks,
                       List<AdminAssistantEvidence.Ref> refs) {
        public Turn { if (refs == null) refs = List.of(); }
    }

    record TurnView(String question, String answer, String tool, Instant queriedAt, List<Task> tasks,
                           List<AdminAssistantEvidence.Card> records) {}

    record Reply(String sessionId, TurnView turn) {}


}
