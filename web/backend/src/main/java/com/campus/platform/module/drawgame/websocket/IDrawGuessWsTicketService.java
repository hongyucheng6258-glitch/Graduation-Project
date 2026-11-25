package com.campus.platform.module.drawgame.websocket;

import com.campus.platform.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * DrawGuessWsTicketService 服务接口。
 */
public interface IDrawGuessWsTicketService {

    /** 票据声明 */
    record TicketClaims(Long userId, Long roomId) { }

    String issue(Long userId, Long roomId);

    TicketClaims consume(String ticket);
}
