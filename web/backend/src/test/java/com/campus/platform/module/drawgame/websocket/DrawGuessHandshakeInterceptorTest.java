package com.campus.platform.module.drawgame.websocket;

import com.campus.platform.module.drawgame.service.DrawGuessRoomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrawGuessHandshakeInterceptorTest {
    @Mock
    private DrawGuessWsTicketService ticketService;
    @Mock
    private DrawGuessRoomService roomService;
    @Mock
    private ServerHttpResponse response;

    @Test
    void handshakeBindsOnlyTheTicketUserAndRoomAfterMembershipCheck() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setParameter("ticket", "single-use-ticket");
        when(ticketService.consume("single-use-ticket"))
                .thenReturn(new DrawGuessWsTicketService.TicketClaims(19L, 7L));
        when(roomService.isActiveMember(7L, 19L)).thenReturn(true);
        HashMap<String, Object> attributes = new HashMap<>();

        boolean accepted = new DrawGuessHandshakeInterceptor(ticketService, roomService).beforeHandshake(
                new ServletServerHttpRequest(servletRequest), response, null, attributes);

        assertTrue(accepted);
        assertEquals(19L, attributes.get(DrawGuessSessionRegistry.USER_ID_ATTRIBUTE));
        assertEquals(7L, attributes.get(DrawGuessSessionRegistry.ROOM_ID_ATTRIBUTE));
        verify(roomService).isActiveMember(7L, 19L);
    }

    @Test
    void expiredTicketAndNonMemberCannotOpenSocket() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setParameter("ticket", "expired");
        when(ticketService.consume("expired")).thenReturn(null);
        var interceptor = new DrawGuessHandshakeInterceptor(ticketService, roomService);

        assertFalse(interceptor.beforeHandshake(new ServletServerHttpRequest(servletRequest),
                response, null, new HashMap<>()));
        verifyNoInteractions(roomService);

        servletRequest.setParameter("ticket", "not-member");
        when(ticketService.consume("not-member"))
                .thenReturn(new DrawGuessWsTicketService.TicketClaims(20L, 7L));
        when(roomService.isActiveMember(7L, 20L)).thenReturn(false);
        assertFalse(interceptor.beforeHandshake(new ServletServerHttpRequest(servletRequest),
                response, null, new HashMap<>()));
    }
}
