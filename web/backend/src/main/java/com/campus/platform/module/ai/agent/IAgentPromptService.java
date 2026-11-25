package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.mapper.AgentPromptMapper;
import com.campus.platform.module.ai.mapper.AgentPromptMapper.NewVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * AgentPromptService 服务接口。
 */
public interface IAgentPromptService {

    record Snapshot(long id, String content) {}

    record Version(long id, String content, LocalDateTime createdAt, boolean active) {}

    Snapshot active(String scene, String defaultPrompt);

    List<Version> versions(String scene);

    long create(String scene, String content, long adminId);

    void publish(String scene, long id);
}
