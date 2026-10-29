package com.campus.platform.module.chat.websocket;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ChatWebSocketConfig implements WebSocketConfigurer {
    private final ChatWebSocketHandler handler;
    private final ChatHandshakeInterceptor handshakeInterceptor;

    @Value("${security.trusted-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String[] trustedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/chat")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(trustedOrigins);
    }
}
