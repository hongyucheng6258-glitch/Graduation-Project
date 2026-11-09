package com.campus.platform.module.ai.adminassistant;

import com.campus.platform.common.R;
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
    private final AdminAssistantService assistant;
    private final AdminAssistantAccess access;
    private final AdminAssistantReviewActions reviews;
    private final AdminAssistantNoticeDrafts notices;
    private final AdminAssistantDiagnostics diagnostics;

    @Value("${campus.agent.rag.enabled:false}")
    private boolean ragEnabled;

    @GetMapping("/capabilities")
    public R<Map<String, Boolean>> capabilities() {
        boolean enabled = access.enabled();
        boolean canManageRules = enabled && "super".equals(access.requireAccess().getRole());
        return R.ok(Map.of("enabled", enabled, "ragEnabled", enabled && ragEnabled, "canManageRules", canManageRules));
    }

    @GetMapping("/sessions")
    public R<List<AdminAssistantSessions.Session>> sessions() {
        return R.ok(sessions.list(adminId()));
    }

    @GetMapping("/sessions/{id}")
    public R<List<AdminAssistantService.TurnView>> history(@PathVariable String id) {
        return R.ok(assistant.history(adminId(), id));
    }

    @PostMapping("/ask")
    public R<AdminAssistantService.Reply> ask(@Valid @RequestBody Question question) {
        return R.ok(assistant.ask(adminId(), question.sessionId(), question.question().strip()));
    }

    @GetMapping("/sessions/{id}/reviews")
    public R<List<AdminAssistantReviewActions.Card>> reviews(@PathVariable String id) {
        return R.ok(reviews.list(adminId(), id));
    }

    @PostMapping("/reviews/prepare")
    public R<AdminAssistantReviewActions.Card> prepareReview(@Valid @RequestBody AdminAssistantReviewActions.Request request) {
        return R.ok(reviews.prepare(adminId(), request));
    }

    @PostMapping("/reviews/{id}/confirm")
    public R<AdminAssistantReviewActions.Card> confirmReview(@PathVariable String id) {
        return R.ok(reviews.confirm(adminId(), id));
    }

    @GetMapping("/notice-drafts")
    public R<List<AdminAssistantNoticeDrafts.Draft>> noticeDrafts() {
        return R.ok(notices.list(adminId()));
    }

    @PostMapping("/notice-drafts")
    public R<AdminAssistantNoticeDrafts.Draft> prepareNotice(@Valid @RequestBody NoticeRequest request) {
        return R.ok(notices.prepare(adminId(), request.request().strip()));
    }

    @GetMapping("/notice-drafts/{id}")
    public R<AdminAssistantNoticeDrafts.Draft> noticeDraft(@PathVariable String id) {
        return R.ok(notices.get(adminId(), id));
    }

    @PostMapping("/notice-drafts/{id}/confirm")
    public R<AdminAssistantNoticeDrafts.Draft> confirmNotice(@PathVariable String id, @Valid @RequestBody AdminAssistantNoticeDrafts.Form form) {
        return R.ok(notices.confirm(adminId(), id, form));
    }

    @GetMapping("/diagnostics")
    public R<AdminAssistantDiagnostics.Summary> diagnostics(@RequestParam(defaultValue = "1") int days) {
        adminId();
        return R.ok(diagnostics.summarize(days));
    }

    private Long adminId() { return access.requireAccess().getId(); }

    public record NoticeRequest(@NotBlank @Size(max = 1200) String request) {}
    public record Question(@NotBlank @Size(max = 1200) String question,
                           @Pattern(regexp = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}") String sessionId) {}
}
