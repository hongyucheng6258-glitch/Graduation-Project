package com.campus.platform.module.ai.gateway;

import com.campus.platform.common.mapper.MapperQueries;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.module.ai.entity.AiMessage;
import com.campus.platform.module.ai.mapper.AiMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ChatMemoryService 服务接口。
 */
public interface IChatMemoryService {

    Long saveMessage(Long sessionId, String role, String content, Integer tokens);

    List<AiMessage> getRecentContext(Long sessionId);

    void deleteBySession(Long sessionId);
}
