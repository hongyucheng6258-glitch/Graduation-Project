package com.campus.platform.module.ai.agent;

import com.campus.platform.module.ai.entity.AiMessage;
import com.campus.platform.module.ai.gateway.AiConfigHolder;
import com.campus.platform.module.ai.rag.KnowledgeService;
import com.campus.platform.module.idle.service.IdleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class AgentActivityPublishRoutingTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AgentModelClient model = mock(AgentModelClient.class);
    private final AgentConversation conversation = mock(AgentConversation.class);
    private final AgentPromptService prompts = mock(AgentPromptService.class);

    @Test
    void preparesNamedActivityDespiteAnOldRefusalInTheSameConversation() {
        var oldAnswer = new AiMessage();
        oldAnswer.setRole("assistant");
        oldAnswer.setContent("没有发布活动的功能。");
        when(conversation.history(1L, 34L)).thenReturn(List.of(oldAnswer));
        var reply = service().ask(1L, "帮我发布一个读书会", 34L);

        assertEquals(List.of(ActivityPublishDrafts.PREPARE), reply.toolsUsed());
        assertEquals("读书会", reply.activityDrafts().get(0).title());
        assertEquals("DRAFT", reply.activityDrafts().get(0).state());
        assertTrue(reply.answer().contains("编辑并提交活动"));
        verifyNoInteractions(model);
    }

    @Test
    void asksForATitleWhenTheRequestOnlyNamesTheActivityCategory() {
        var reply = service().ask(1L, "我想发布一个校园活动", null);

        assertTrue(reply.activityDrafts().isEmpty());
        assertTrue(reply.answer().contains("活动名称"));
        verifyNoInteractions(model);
    }

    @ParameterizedTest
    @ValueSource(strings = {"帮我发布一个闲置键盘", "帮我发布一个名为《读书会》的活动",
            "帮我发布一个明天下午三点在图书馆举办的读书会", "帮我发布活动：《读书会》地点《图书馆》"})
    void leavesRequestsThatNeedModelParsingOnTheExistingRoute(String question) {
        var reply = service().ask(1L, question, null);

        assertTrue(reply.activityDrafts().isEmpty());
        verify(model).complete(anyList(), anyList(), any());
    }

    private AgentService service() {
        when(model.complete(anyList(), anyList(), any())).thenReturn(json.createObjectNode().put("content", "没有发布活动的功能。"));
        when(conversation.latestIdleSearch(nullable(Long.class))).thenReturn("");
        when(prompts.active("campus_agent", "")).thenReturn(new AgentPromptService.Snapshot(0L, ""));
        var tool = new AgentTool(ActivityPublishDrafts.PREPARE, "test", Map.of("title", "string"), List.of("title"),
                (user, args) -> json.createObjectNode().put("id", "test-draft").put("state", "DRAFT")
                        .put("title", args.path("title").asText()));
        ObjectProvider<KnowledgeService> knowledge = mock();
        return new AgentService(model, json, mock(AgentQuota.class), conversation, prompts, mock(AiConfigHolder.class),
                mock(JdbcTemplate.class), List.of(() -> List.of(tool)), knowledge, mock(IdleService.class), mock(CampusResourceTools.class));
    }
}
