package com.campus.platform.module.ai.agent;

import com.campus.platform.module.ai.mapper.AgentRunMapper;
import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai/agent")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campus.agent.enabled", havingValue = "true")
public class AdminAgentController {
    private final AgentPromptService prompts;
    private final AgentRunMapper runs;

    @GetMapping("/prompts/{scene}")
    public Result<List<AgentPromptService.Version>> list(@PathVariable String scene) {
        return Result.ok(prompts.versions(scene));
    }

    @PostMapping("/prompts/{scene}")
    public Result<Long> create(@PathVariable String scene, @Valid @RequestBody Draft draft) {
        return Result.ok(prompts.create(scene, draft.content().strip(), UserContext.getUid()));
    }

    @PutMapping("/prompts/{scene}/{version}")
    public Result<Void> publish(@PathVariable String scene, @PathVariable long version) {
        prompts.publish(scene, version);
        return Result.ok();
    }

    @GetMapping("/runs")
    public Result<List<Map<String, Object>>> runs() {
        return Result.ok(runs.findRecent());
    }

    public record Draft(@NotBlank @Size(max = 4000) String content) {}
}
