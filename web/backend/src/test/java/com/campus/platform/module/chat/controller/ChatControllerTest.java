package com.campus.platform.module.chat.controller;


import com.campus.platform.module.chat.entity.ChatConversation;
import com.campus.platform.module.chat.service.ChatService;
import com.campus.platform.module.chat.vo.ConversationVO;
import com.campus.platform.module.chat.websocket.ChatWsTicketService;
import com.campus.platform.common.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatControllerTest {
    private ChatService chatService;
    private ChatWsTicketService ticketService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        ticketService = mock(ChatWsTicketService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService, ticketService)).build();
        UserContext.set(7L, "student");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createsConversationWithBusinessContext() throws Exception {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(10L);
        ConversationVO vo = new ConversationVO();
        vo.setId(10L);
        when(chatService.createConversation(7L, 8L, "idle", 99L, "二手教材")).thenReturn(conversation);
        when(chatService.conversation(7L, 10L)).thenReturn(vo);

        mockMvc.perform(post("/api/chat/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":8,\"contextType\":\"idle\",\"contextId\":99,\"contextTitle\":\"二手教材\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    void exposesUnreadCountAndSingleUseTicket() throws Exception {
        when(chatService.unreadCount(7L)).thenReturn(3L);
        when(ticketService.issue(7L)).thenReturn("ticket-1");

        mockMvc.perform(get("/api/chat/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3));
        mockMvc.perform(post("/api/chat/ws-ticket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticket").value("ticket-1"));
    }

    @Test
    void listsAndHidesConversations() throws Exception {
        when(chatService.conversations(7L)).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mockMvc.perform(delete("/api/chat/conversations/10"))
                .andExpect(status().isOk());

        verify(chatService).hideConversation(7L, 10L);
    }
}
