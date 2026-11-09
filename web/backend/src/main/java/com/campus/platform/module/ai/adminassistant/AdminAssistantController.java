package com.campus.platform.module.ai.adminassistant;

import com.campus.platform.common.BizException;
import com.campus.platform.common.R;
import com.campus.platform.common.ResultCode;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.admin.service.AdminPermissionService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/assistant")
@RequiredArgsConstructor
public class AdminAssistantController {
    private final AdminAssistantSessions sessions;
    private final AdminAssistantService assistant;
    private final AdminPermissionService permissions;
    private final AdminAssistantReviewActions reviews;

    @Value("${CAMPUS_ADMIN_ASSISTANT_ENABLED:false}")
    private boolean enabled;

    @GetMapping("/capabilities")
    public R<Map<String, Boolean>> capabilities() {
        return R.ok(Map.of("enabled", enabled));
    }

    @GetMapping("/sessions")
    public R<List<AdminAssistantSessions.Session>> sessions() {
        return R.ok(sessions.list(requireAccess()));
    }

    @GetMapping("/sessions/{id}")
    public R<List<AdminAssistantService.TurnView>> history(@PathVariable String id) {
        return R.ok(assistant.history(requireAccess(), id));
    }

    @PostMapping("/ask")
    public R<AdminAssistantService.Reply> ask(@Valid @RequestBody Question question) {
        return R.ok(assistant.ask(requireAccess(), question.sessionId(), question.question().strip()));
    }

    @GetMapping("/sessions/{id}/reviews")
    public R<List<AdminAssistantReviewActions.Card>> reviews(@PathVariable String id) {
        return R.ok(reviews.list(requireAccess(), id));
    }

    @PostMapping("/reviews/prepare")
    public R<AdminAssistantReviewActions.Card> prepareReview(@Valid @RequestBody AdminAssistantReviewActions.Request request) {
        return R.ok(reviews.prepare(requireAccess(), request));
    }

    @PostMapping("/reviews/{id}/confirm")
    public R<AdminAssistantReviewActions.Card> confirmReview(@PathVariable String id) {
        return R.ok(reviews.confirm(requireAccess(), id));
    }

    private Long requireAccess() {
        if (!enabled) throw new BizException(ResultCode.AI_NOT_CONFIGURED, "管理智能助手尚未启用");
        Long uid = UserContext.getUid();
        String role = permissions.requireActive(uid).getRole();
        if (!"audit".equals(role) && !"super".equals(role)) throw new BizException(ResultCode.FORBIDDEN);
        return uid;
    }

    public record Question(@NotBlank @Size(max = 1200) String question,
                           @Pattern(regexp = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}") String sessionId) {}
}
