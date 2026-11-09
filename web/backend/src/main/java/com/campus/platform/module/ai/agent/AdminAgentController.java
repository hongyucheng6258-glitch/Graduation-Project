package com.campus.platform.module.ai.agent;

import com.campus.platform.common.R;
import com.campus.platform.common.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai/agent")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campus.agent.enabled", havingValue = "true")
public class AdminAgentController {
    private final AgentPromptService prompts;
    private final JdbcTemplate jdbc;

    @GetMapping("/prompts/{scene}")
    public R<List<AgentPromptService.Version>> list(@PathVariable String scene) {
        return R.ok(prompts.versions(scene));
    }

    @PostMapping("/prompts/{scene}")
    public R<Long> create(@PathVariable String scene, @Valid @RequestBody Draft draft) {
        return R.ok(prompts.create(scene, draft.content().strip(), UserContext.getUid()));
    }

    @PutMapping("/prompts/{scene}/{version}")
    public R<Void> publish(@PathVariable String scene, @PathVariable long version) {
        prompts.publish(scene, version);
        return R.ok();
    }

    @GetMapping("/runs")
    public R<List<Map<String, Object>>> runs() {
        return R.ok(jdbc.queryForList("""
                SELECT id,prompt_version,model_name,tools,elapsed_ms,status,created_at
                FROM agent_run ORDER BY id DESC LIMIT 100
                """));
    }

    public record Draft(@NotBlank @Size(max = 4000) String content) {}
}
