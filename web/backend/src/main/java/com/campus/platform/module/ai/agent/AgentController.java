package com.campus.platform.module.ai.agent;

import com.campus.platform.common.R;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/agent")
public class AgentController {
    private final AgentService agent;
    private final ActivitySignupActions actions;
    private final boolean enabled;
    private final boolean ragEnabled;

    public AgentController(AgentService agent, ActivitySignupActions actions,
                           @Value("${campus.agent.enabled:false}") boolean enabled,
                           @Value("${campus.agent.rag.enabled:false}") boolean ragEnabled) {
        this.agent = agent;
        this.actions = actions;
        this.enabled = enabled;
        this.ragEnabled = ragEnabled;
    }

    @GetMapping("/capabilities")
    public R<Capabilities> capabilities() {
        return R.ok(new Capabilities(enabled, ragEnabled));
    }

    @PostMapping("/ask")
    public R<AgentService.Reply> ask(@Valid @RequestBody Question request) {
        checkEnabled();
        return R.ok(agent.ask(UserContext.getUid(), request.question().strip(), request.sessionId()));
    }

    @PostMapping("/actions/{id}/confirm")
    public R<ActivitySignupActions.Confirmation> confirm(@PathVariable String id) {
        checkEnabled();
        return R.ok(actions.confirm(UserContext.getUid(), id));
    }

    private void checkEnabled() {
        if (!enabled) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED, "新版校园向导未启用");
        }
    }

    public record Question(@NotBlank @Size(max = 2000) String question, Long sessionId) {}
    public record Capabilities(boolean enabled, boolean ragEnabled) {}
}
