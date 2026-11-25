package com.campus.platform.module.chat.websocket;

import com.campus.platform.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * ChatWsTicketService 服务接口。
 */
public interface IChatWsTicketService {

    String issue(Long userId);

    Long consume(String ticket);
}
