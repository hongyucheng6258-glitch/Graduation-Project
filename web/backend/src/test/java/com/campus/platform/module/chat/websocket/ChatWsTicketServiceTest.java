package com.campus.platform.module.chat.websocket;


import com.campus.platform.utils.RedisUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatWsTicketServiceTest {

    @Mock
    private RedisUtils redisUtils;

    @Test
    void ticketIsSingleUseAndCarriesUserId() {
        ChatWsTicketService service = new ChatWsTicketService(redisUtils);
        when(redisUtils.getAndDelete("chat:ws-ticket:t-1")).thenReturn(7L, null);

        assertEquals(7L, service.consume("t-1"));
        assertNull(service.consume("t-1"));
        verify(redisUtils, times(2)).getAndDelete("chat:ws-ticket:t-1");
    }

    @Test
    void expiredOrForgedTicketIsRejected() {
        ChatWsTicketService service = new ChatWsTicketService(redisUtils);
        when(redisUtils.getAndDelete("chat:ws-ticket:bad")).thenReturn(null);

        assertNull(service.consume("bad"));
        verify(redisUtils).getAndDelete("chat:ws-ticket:bad");
    }
}
