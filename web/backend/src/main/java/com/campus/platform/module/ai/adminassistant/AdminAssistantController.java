package com.campus.platform.module.ai.adminassistant;

import com.campus.platform.module.admin.audit.AdminOperation;
import com.campus.platform.module.admin.audit.AdminPermission;

import com.campus.platform.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/assistant")
@RequiredArgsConstructor
public class AdminAssistantController {
    private final AdminAssistantSessions sessions;
    private final IAdminAssistantService assistant;
    private final AdminAssistantAccess access;
    private final AdminAssistantReviewActions reviews;
    private final AdminAssistantNoticeDrafts notices;
    private final AdminAssistantDiagnostics diagnostics;

    @Value("${campus.agent.rag.enabled:false}")
    private boolean ragEnabled;

    @GetMapping("/capabilities")
    public Result<Map<String, Boolean>> capabilities() {
        boolean enabled = access.enabled();
        boolean canManageRules = enabled && "super".equals(access.requireAccess().getRole());
        return Result.ok(Map.of("enabled", enabled, "ragEnabled", enabled && ragEnabled, "canManageRules", canManageRules));
    }

    @GetMapping("/sessions")
    public Result<List<AdminAssistantSessions.Session>> sessions() {
        return Result.ok(sessions.list(adminId()));
    }

    @GetMapping("/sessions/{id}")
    public Result<List<IAdminAssistantService.TurnView>> history(@PathVariable String id) {
        return Result.ok(assistant.history(adminId(), id));
    }

    @PostMapping("/ask")
    public Result<IAdminAssistantService.Reply> ask(@Valid @RequestBody Question question) {
        return Result.ok(assistant.ask(adminId(), question.sessionId(), question.question().strip()));
    }

    @GetMapping("/sessions/{id}/reviews")
    public Result<List<AdminAssistantReviewActions.Card>> reviews(@PathVariable String id) {
        return Result.ok(reviews.list(adminId(), id));
    }

    @PostMapping("/reviews/prepare")
    public Result<AdminAssistantReviewActions.Card> prepareReview(@Valid @RequestBody AdminAssistantReviewActions.Request request) {
        return Result.ok(reviews.prepare(adminId(), request));
    }

    @PostMapping("/reviews/{id}/confirm")
    @AdminOperation(permission = AdminPermission.CONTENT_REVIEW, module = "audit", action = "confirm-review", assistant = true)
    public Result<AdminAssistantReviewActions.Card> confirmReview(@PathVariable String id) {
        return Result.ok(reviews.confirm(adminId(), id));
    }

    @GetMapping("/notice-drafts")
    public Result<List<AdminAssistantNoticeDrafts.Draft>> noticeDrafts() {
        return Result.ok(notices.list(adminId()));
    }

    @PostMapping("/notice-drafts")
    public Result<AdminAssistantNoticeDrafts.Draft> prepareNotice(@Valid @RequestBody NoticeRequest request) {
        return Result.ok(notices.prepare(adminId(), request.request().strip()));
    }

    @GetMapping("/notice-drafts/{id}")
    public Result<AdminAssistantNoticeDrafts.Draft> noticeDraft(@PathVariable String id) {
        return Result.ok(notices.get(adminId(), id));
    }

    @PostMapping("/notice-drafts/{id}/confirm")
    @AdminOperation(permission = AdminPermission.NOTICE_WRITE, module = "notice", action = "confirm-draft", assistant = true)
    public Result<AdminAssistantNoticeDrafts.Draft> confirmNotice(@PathVariable String id, @Valid @RequestBody AdminAssistantNoticeDrafts.Form form) {
        return Result.ok(notices.confirm(adminId(), id, form));
    }

    @GetMapping("/diagnostics")
    public Result<AdminAssistantDiagnostics.Summary> diagnostics(@RequestParam(defaultValue = "1") int days) {
        adminId();
        return Result.ok(diagnostics.summarize(days));
    }

    private Long adminId() { return access.requireAccess().getId(); }

    public record NoticeRequest(@NotBlank @Size(max = 1200) String request) {}
    public record Question(@NotBlank @Size(max = 1200) String question,
                           @Pattern(regexp = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}") String sessionId) {}
}
