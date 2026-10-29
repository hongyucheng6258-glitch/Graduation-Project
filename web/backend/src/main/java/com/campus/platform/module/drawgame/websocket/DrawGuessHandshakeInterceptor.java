package com.campus.platform.module.drawgame.websocket;

import com.campus.platform.module.drawgame.service.DrawGuessRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DrawGuessHandshakeInterceptor implements HandshakeInterceptor {
    private final DrawGuessWsTicketService ticketService;
    private final DrawGuessRoomService roomService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) return false;
        String ticket = servletRequest.getServletRequest().getParameter("ticket");
        DrawGuessWsTicketService.TicketClaims claims = ticketService.consume(ticket);
        if (claims == null || !roomService.isActiveMember(claims.roomId(), claims.userId())) return false;
        attributes.put(DrawGuessSessionRegistry.USER_ID_ATTRIBUTE, claims.userId());
        attributes.put(DrawGuessSessionRegistry.ROOM_ID_ATTRIBUTE, claims.roomId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) { }
}
