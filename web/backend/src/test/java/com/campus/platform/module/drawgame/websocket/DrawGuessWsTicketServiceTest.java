package com.campus.platform.module.drawgame.websocket;

import com.campus.platform.utils.RedisUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrawGuessWsTicketServiceTest {
    @Mock
    private RedisUtils redisUtils;

    @Test
    void issuedTicketIsShortLivedAndBindsBothUserAndRoom() {
        DrawGuessWsTicketService service = new DrawGuessWsTicketService(redisUtils);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);

        String ticket = service.issue(19L, 7L);

        verify(redisUtils).set(key.capture(), eq("19:7"), eq(60L), eq(TimeUnit.SECONDS));
        assertTrue(key.getValue().startsWith("draw-guess:ws-ticket:"));
        assertEquals(36, ticket.length());
        when(redisUtils.getAndDelete(key.getValue())).thenReturn("19:7");
        assertEquals(new DrawGuessWsTicketService.TicketClaims(19L, 7L), service.consume(ticket));
    }

    @Test
    void missingExpiredOrMalformedTicketIsRejected() {
        DrawGuessWsTicketService service = new DrawGuessWsTicketService(redisUtils);
        when(redisUtils.getAndDelete("draw-guess:ws-ticket:expired")).thenReturn(null);
        when(redisUtils.getAndDelete("draw-guess:ws-ticket:forged")).thenReturn("19:not-a-room");

        assertNull(service.consume(null));
        assertNull(service.consume(" "));
        assertNull(service.consume("expired"));
        assertNull(service.consume("forged"));
    }
}
