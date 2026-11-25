package com.campus.platform.module.ai.rag;

import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/ai/knowledge")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campus.agent.rag.enabled", havingValue = "true")
public class KnowledgeController {
    private final KnowledgeService knowledge;
    private final KnowledgeAnswerService answers;

    @PostMapping("/pdf/{pdfId}/index")
    public Result<KnowledgeService.Status> index(@PathVariable long pdfId,
                                            @RequestParam(defaultValue = "false") boolean rebuild) {
        return Result.ok(knowledge.enqueue(UserContext.getUid(), pdfId, rebuild));
    }

    @GetMapping("/{id}")
    public Result<KnowledgeService.Status> status(@PathVariable long id) {
        return Result.ok(knowledge.status(id, UserContext.getUid()));
    }

    @PostMapping("/{id}/ask")
    public Result<KnowledgeAnswerService.Reply> ask(@PathVariable long id, @Valid @RequestBody Question request) {
        return Result.ok(answers.ask(UserContext.getUid(), id, request.question().strip(), request.sessionId()));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable long id) {
        var pdf = knowledge.file(id, UserContext.getUid());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", ContentDisposition.attachment()
                        .filename(pdf.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(pdf.bytes());
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        knowledge.delete(id, UserContext.getUid());
        return Result.ok();
    }

    public record Question(@NotBlank @Size(max = 2000) String question, Long sessionId) {}
}
