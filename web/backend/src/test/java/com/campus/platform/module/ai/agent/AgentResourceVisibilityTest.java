package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.module.ai.gateway.AiConfigHolder;
import com.campus.platform.module.ai.rag.IKnowledgeService;
import com.campus.platform.module.idle.service.IIdleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.campus.platform.module.ai.mapper.AgentRunMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentResourceVisibilityTest {
    @Test
    void retriesExplicitResourceLookupInsteadOfReturningUnverifiedModelText() {
        var json = new ObjectMapper();
        var model = mock(AgentModelClient.class);
        var request = json.createObjectNode();
        request.putArray("tool_calls").addObject().put("id", "tool-2").putObject("function")
                .put("name", CampusResourceTools.SEARCH).put("arguments", "{}");
        when(model.complete(anyList(), anyList(), any())).thenReturn(
                json.createObjectNode().put("content", "编造的选课结果"), request,
                json.createObjectNode().put("content", "找到相关选课问题"));
        var data = json.createObjectNode().put("kind", "qa");
        data.putArray("items").addObject().put("id", 25);
        var tool = new AgentTool(CampusResourceTools.SEARCH, "test", Map.of(), List.of(), (user, args) -> data);
        var conversation = mock(AgentConversation.class);
        when(conversation.latestIdleSearch(null)).thenReturn("");
        var prompts = mock(IAgentPromptService.class);
        when(prompts.active("campus_agent", "")).thenReturn(new IAgentPromptService.Snapshot(0L, ""));
        var resources = mock(CampusResourceTools.class);
        when(resources.cards(List.of(new CampusResourceTools.Ref("qa", 25))))
                .thenReturn(List.of(new CampusResourceTools.Card("qa", 25, "真实选课问题", "课程", 0, null)));
        ObjectProvider<IKnowledgeService> knowledge = mock();
        var service = new AgentServiceImpl(model, json, mock(AgentQuota.class), conversation, prompts,
                mock(AiConfigHolder.class), mock(AgentRunMapper.class), List.of(() -> List.of(tool)),
                knowledge, mock(IIdleService.class), resources);

        var reply = service.ask(1L, "查校园互助问答里的选课问题", null);
        assertEquals(List.of(CampusResourceTools.SEARCH), reply.toolsUsed());
        assertEquals("真实选课问题", reply.resources().get(0).title());
    }

    @Test
    void rejectsAndDoesNotSaveProseWhenResourceIsWithdrawnDuringGeneration() {
        var json = new ObjectMapper();
        var model = mock(AgentModelClient.class);
        var request = json.createObjectNode();
        request.putArray("tool_calls").addObject().put("id", "tool-1").putObject("function")
                .put("name", CampusResourceTools.SEARCH).put("arguments", "{}");
        when(model.complete(anyList(), anyList(), any())).thenReturn(request,
                json.createObjectNode().put("content", "找到了刚撤回的内容"));
        var data = json.createObjectNode().put("kind", "lost");
        data.putArray("items").addObject().put("id", 15);
        var tool = new AgentTool(CampusResourceTools.SEARCH, "test", Map.of(), List.of(), (user, args) -> data);
        var conversation = mock(AgentConversation.class);
        when(conversation.latestIdleSearch(null)).thenReturn("");
        var prompts = mock(IAgentPromptService.class);
        when(prompts.active("campus_agent", "")).thenReturn(new IAgentPromptService.Snapshot(0L, ""));
        var resources = mock(CampusResourceTools.class);
        when(resources.cards(List.of(new CampusResourceTools.Ref("lost", 15)))).thenReturn(List.of());
        ObjectProvider<IKnowledgeService> knowledge = mock();
        var service = new AgentServiceImpl(model, json, mock(AgentQuota.class), conversation, prompts,
                mock(AiConfigHolder.class), mock(AgentRunMapper.class), List.of(() -> List.of(tool)),
                knowledge, mock(IIdleService.class), resources);

        var failure = assertThrows(BizException.class, () -> service.ask(1L, "查询失物", null));
        assertEquals("查询期间校园内容有更新，请重新查询", failure.getMessage());
        verify(conversation, never()).save(any(), any(), any(), any(), any(), any());
    }
}
