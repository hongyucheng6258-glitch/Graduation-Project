package com.campus.platform.module.admin.controller;

import com.campus.platform.module.admin.service.AiAdminService;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.R;
import com.campus.platform.module.ai.dto.AiConfigUpdateDTO;
import com.campus.platform.module.ai.dto.PromptTemplateDTO;
import com.campus.platform.module.ai.entity.AiCallLog;
import com.campus.platform.module.ai.entity.AiConfig;
import com.campus.platform.module.ai.entity.PromptTemplate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {
    private final AiAdminService aiAdminService;

    @GetMapping("/config")
    public R<List<AiConfig>> listConfigs() {
        return R.ok(aiAdminService.listConfigs());
    }

    @PutMapping("/config")
    public R<Void> updateConfigs(@Valid @RequestBody AiConfigUpdateDTO dto) {
        aiAdminService.updateConfigs(dto.getConfigs());
        return R.ok();
    }

    @GetMapping("/prompt")
    public R<List<PromptTemplate>> listPrompts(@RequestParam(required = false) String scene) {
        return R.ok(aiAdminService.listPrompts(scene));
    }

    @PostMapping("/prompt")
    public R<PromptTemplate> createPrompt(@Valid @RequestBody PromptTemplateDTO dto) {
        return R.ok(aiAdminService.createPrompt(dto));
    }

    @PutMapping("/prompt/{id}")
    public R<PromptTemplate> updatePrompt(@PathVariable Long id, @Valid @RequestBody PromptTemplateDTO dto) {
        return R.ok(aiAdminService.updatePrompt(id, dto));
    }

    @GetMapping("/log")
    public R<PageResult<AiCallLog>> listLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(aiAdminService.listLogs(userId, scene, status, pageNum, pageSize));
    }
}
