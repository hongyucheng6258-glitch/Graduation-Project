package com.campus.platform.module.ai.rag;

import com.campus.platform.common.R;
import com.campus.platform.module.ai.adminassistant.AdminAssistantAccess;
import com.campus.platform.module.ai.mapper.PdfDocumentMapper;
import com.campus.platform.module.ai.service.PdfService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/assistant/rules")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campus.agent.rag.enabled", havingValue = "true")
public class AdminRulesController {
    private static final String SCOPE = "ADMIN";
    private final AdminAssistantAccess access;
    private final KnowledgeService knowledge;
    private final KnowledgeAnswerService answers;
    private final PdfService pdfService;
    private final PdfDocumentMapper pdfs;

    @GetMapping
    public R<List<KnowledgeService.PublicDocument>> list() {
        access.requireAccess();
        return R.ok(knowledge.documents(SCOPE));
    }

    @PostMapping
    @Transactional
    public R<KnowledgeService.Status> upload(@RequestParam MultipartFile file) {
        var admin = access.requireSuper();
        var uploaded = pdfService.upload(-admin.getId(), file);
        return R.ok(knowledge.enqueuePdf(pdfs.selectById(uploaded.getDocId()), SCOPE));
    }

    @PostMapping("/{id}/reindex")
    public R<KnowledgeService.Status> reindex(@PathVariable long id) {
        access.requireSuper();
        return R.ok(knowledge.reindexScoped(id, SCOPE));
    }

    @DeleteMapping("/{id}")
    public R<Void> withdraw(@PathVariable long id) {
        access.requireSuper();
        knowledge.remove(id, null, SCOPE);
        return R.ok();
    }

    @PostMapping("/ask")
    public R<KnowledgeAnswerService.InternalReply> ask(@Valid @RequestBody Question request) {
        access.charge(access.requireAccess().getId());
        return R.ok(answers.askInternal(request.question().strip()));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable long id, @RequestParam String version) {
        access.requireAccess();
        var pdf = knowledge.internalFile(id, version);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
                .header("X-Content-Type-Options", "nosniff").body(pdf.bytes());
    }

    public record Question(@NotBlank @Size(max = 1200) String question) {}
}
