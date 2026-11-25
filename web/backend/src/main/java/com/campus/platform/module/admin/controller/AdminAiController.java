package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.audit.AdminOperation;
import com.campus.platform.module.admin.audit.AdminPermission;

import com.campus.platform.module.admin.service.IAiAdminService;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.Result;
import com.campus.platform.module.ai.dto.AiConfigUpdateDTO;
import com.campus.platform.module.ai.dto.PromptTemplateDTO;
import com.campus.platform.module.ai.entity.AiCallLog;
import com.campus.platform.module.ai.entity.AiConfig;
import com.campus.platform.module.ai.entity.PromptTemplate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {
    private final IAiAdminService aiAdminService;
    @Value("${campus.agent.enabled:false}")
    private boolean agentEnabled;
    @Value("${campus.agent.rag.enabled:false}")
    private boolean ragEnabled;

    @GetMapping("/agent/capabilities")
    public Result<Map<String, Boolean>> agentCapabilities() {
        return Result.ok(Map.of("enabled", agentEnabled, "ragEnabled", ragEnabled));
    }

    @GetMapping("/config")
    public Result<List<AiConfig>> listConfigs() {
        return Result.ok(aiAdminService.listConfigs());
    }

    @PutMapping("/config")
    @AdminOperation(permission = AdminPermission.AI_CONFIG_EDIT, module = "ai", action = "update-config")
    public Result<Void> updateConfigs(@Valid @RequestBody AiConfigUpdateDTO dto) {
        aiAdminService.updateConfigs(dto.getConfigs());
        return Result.ok();
    }

    @GetMapping("/prompt")
    public Result<List<PromptTemplate>> listPrompts(@RequestParam(required = false) String scene) {
        return Result.ok(aiAdminService.listPrompts(scene));
    }

    @PostMapping("/prompt")
    @AdminOperation(permission = AdminPermission.AI_PROMPT_EDIT, module = "ai", action = "create-prompt")
    public Result<PromptTemplate> createPrompt(@Valid @RequestBody PromptTemplateDTO dto) {
        return Result.ok(aiAdminService.createPrompt(dto));
    }

    @PutMapping("/prompt/{id}")
    @AdminOperation(permission = AdminPermission.AI_PROMPT_EDIT, module = "ai", action = "update-prompt", idArgument = 0)
    public Result<PromptTemplate> updatePrompt(@PathVariable Long id, @Valid @RequestBody PromptTemplateDTO dto) {
        return Result.ok(aiAdminService.updatePrompt(id, dto));
    }

    @GetMapping("/log")
    public Result<PageResult<AiCallLog>> listLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(aiAdminService.listLogs(userId, scene, status, pageNum, pageSize));
    }
}
