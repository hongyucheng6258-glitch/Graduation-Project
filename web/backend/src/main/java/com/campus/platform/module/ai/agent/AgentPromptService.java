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

@Service
@RequiredArgsConstructor
public class AgentPromptService {
    private static final Set<String> SCENES = Set.of("campus_agent", "knowledge_answer");
    private final AgentPromptMapper prompts;

    public Snapshot active(String scene, String defaultPrompt) {
        Snapshot active = prompts.findActive(scene);
        return active == null ? new Snapshot(0L, defaultPrompt) : active;
    }

    public List<Version> versions(String scene) {
        validate(scene);
        return prompts.findVersions(scene);
    }

    public long create(String scene, String content, long adminId) {
        validate(scene);
        var version = new NewVersion(scene, content, adminId);
        prompts.insertVersion(version);
        return java.util.Objects.requireNonNull(version.getId());
    }

    public void publish(String scene, long id) {
        validate(scene);
        if (id == 0) {
            prompts.clearActive(scene);
            return;
        }
        int changed = prompts.setActive(scene, id);
        if (changed == 0 && active(scene, "").id() != id) {
            throw new BizException(ResultCode.NOT_FOUND, "未找到该提示词版本");
        }
    }

    private void validate(String scene) {
        if (!SCENES.contains(scene)) {
            throw new BizException(ResultCode.BAD_REQUEST, "未知的提示词场景");
        }
    }

    public record Snapshot(long id, String content) {}
    public record Version(long id, String content, LocalDateTime createdAt, boolean active) {}
}
