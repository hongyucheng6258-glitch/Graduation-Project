package com.campus.platform.module.ai.agent;

import com.campus.platform.module.ai.mapper.AgentRunMapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.AiConfigHolder;
import com.campus.platform.module.ai.rag.IKnowledgeService;
import com.campus.platform.module.idle.service.IIdleService;
import com.campus.platform.module.idle.vo.IdleGuideCard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AgentService 服务接口。
 */
public interface IAgentService {

    record Citation(long documentId, String fileName, int page) {}

    record Reply(String answer, List<String> toolsUsed, long sessionId, List<Citation> citations, long promptVersion,
                        List<ActivitySignupActions.Proposal> actions, List<IdleGuideCard> idleItems, boolean idleSearch,
                        List<IdlePublishDrafts.Draft> idleDrafts, List<IdleAppointmentActions.Card> idleAppointments,
                        List<CampusResourceTools.Card> resources, List<ActivityPublishDrafts.Draft> activityDrafts,
                        List<CampusPublishDrafts.Draft> campusDrafts) {}

    Reply ask(Long userId, String question, Long sessionId);
}
