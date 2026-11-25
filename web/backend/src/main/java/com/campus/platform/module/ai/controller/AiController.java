package com.campus.platform.module.ai.controller;

import com.campus.platform.module.ai.dto.SessionCreateDTO;
import com.campus.platform.module.ai.service.IAiChatService;
import com.campus.platform.module.ai.dto.CodeFixDTO;
import com.campus.platform.module.ai.dto.OutlineDTO;
import com.campus.platform.module.ai.dto.AiChatDTO;
import com.campus.platform.module.ai.entity.AiMessage;
import com.campus.platform.module.ai.entity.AiSession;
import com.campus.platform.module.ai.dto.PdfAskDTO;
import com.campus.platform.module.ai.dto.QuizDTO;
import com.campus.platform.module.ai.dto.AssistComposeDTO;
import com.campus.platform.module.ai.dto.AssistPolishDTO;
import com.campus.platform.module.ai.dto.GuideAskDTO;
import com.campus.platform.module.ai.service.IAiAssistService;
import com.campus.platform.module.ai.service.ICampusGuideService;
import com.campus.platform.module.ai.agent.AgentMessageAttachments;
import org.springframework.beans.factory.annotation.Value;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
    private final IAiChatService aiChatService;
    private final IAiAssistService aiAssistService;
    private final ICampusGuideService campusGuideService;
    private final AgentMessageAttachments attachments;
    @Value("${campus.agent.enabled:false}")
    private boolean agentEnabled;
    @Value("${campus.agent.rag.enabled:false}")
    private boolean ragEnabled;

    @PostMapping("/session")
    public Result<AiSession> createSession(@Valid @RequestBody SessionCreateDTO dto) {
        return Result.ok(aiChatService.createSession(UserContext.getUid(), dto));
    }

    @GetMapping("/sessions")
    public Result<List<AiSession>> listSessions(@RequestParam(required = false) String scene) {
        return Result.ok(aiChatService.listSessions(UserContext.getUid(), scene));
    }

    @PutMapping("/session/{id}")
    public Result<Void> renameSession(@PathVariable Long id, @RequestBody Map<String, String> body) {
        aiChatService.renameSession(UserContext.getUid(), id, body.get("title"));
        return Result.ok();
    }

    @DeleteMapping("/session/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        aiChatService.deleteSession(UserContext.getUid(), id);
        return Result.ok();
    }

    @PutMapping("/session/{id}/pdf")
    public Result<AiSession> bindPdfDocument(@PathVariable Long id, @RequestBody(required = false) Map<String, Long> body) {
        Long docId = body == null ? null : body.get("docId");
        return Result.ok(aiChatService.bindPdfDocument(UserContext.getUid(), id, docId));
    }

    @GetMapping("/session/{id}/messages")
    public Result<PageResult<AiMessage>> listMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long uid = UserContext.getUid();
        var page = aiChatService.listMessages(uid, id, pageNum, pageSize);
        if (agentEnabled || ragEnabled) {
            attachments.restore(uid, page.getList());
        }
        return Result.ok(page);
    }

    @PostMapping("/chat/stream")
    public SseEmitter askStream(@Valid @RequestBody AiChatDTO dto) {
        return aiChatService.askStream(UserContext.getUid(), dto);
    }

    @PostMapping("/chat/sync")
    public Result<String> askSync(@Valid @RequestBody AiChatDTO dto) {
        return Result.ok(aiChatService.askSync(UserContext.getUid(), dto));
    }

    @PostMapping("/pdf/ask")
    public Result<String> pdfAsk(@Valid @RequestBody PdfAskDTO dto) {
        return Result.ok(aiChatService.pdfAsk(UserContext.getUid(), dto));
    }

    @PostMapping("/pdf/ask/stream")
    public SseEmitter pdfAskStream(@Valid @RequestBody PdfAskDTO dto) {
        return aiChatService.pdfAskStream(UserContext.getUid(), dto);
    }

    @PostMapping("/code-fix")
    public Result<String> codeFix(@Valid @RequestBody CodeFixDTO dto) {
        return Result.ok(aiChatService.codeFix(UserContext.getUid(), dto));
    }

    @PostMapping("/outline")
    public Result<String> outline(@Valid @RequestBody OutlineDTO dto) {
        return Result.ok(aiChatService.outline(UserContext.getUid(), dto));
    }

    @PostMapping("/quiz")
    public Result<String> quiz(@Valid @RequestBody QuizDTO dto) {
        return Result.ok(aiChatService.quiz(UserContext.getUid(), dto));
    }


    /** AI 辅助发布-生成草稿（活动/闲置/失物招领/动态） */
    @PostMapping("/assist/compose")
    public Result<Map<String, Object>> assistCompose(@Valid @RequestBody AssistComposeDTO dto) {
        return Result.ok(aiAssistService.compose(UserContext.getUid(), dto));
    }

    /** AI 辅助发布-润色/扩写/精简 */
    @PostMapping("/assist/polish")
    public Result<String> assistPolish(@Valid @RequestBody AssistPolishDTO dto) {
        return Result.ok(aiAssistService.polish(UserContext.getUid(), dto));
    }

    /** AI 校园向导-业务数据问答（活动/闲置/失物/动态/公告） */
    @PostMapping("/guide/ask")
    public Result<String> guideAsk(@Valid @RequestBody GuideAskDTO dto) {
        return Result.ok(campusGuideService.ask(UserContext.getUid(), dto.getQuestion()));
    }
}
