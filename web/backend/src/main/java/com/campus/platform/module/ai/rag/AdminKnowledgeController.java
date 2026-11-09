package com.campus.platform.module.ai.rag;

import com.campus.platform.common.R;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.ai.mapper.PdfDocumentMapper;
import com.campus.platform.module.ai.service.PdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai/agent/knowledge")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campus.agent.rag.enabled", havingValue = "true")
public class AdminKnowledgeController {
    private final KnowledgeService knowledge;
    private final PdfService pdfService;
    private final PdfDocumentMapper pdfs;

    @GetMapping
    public R<List<KnowledgeService.PublicDocument>> list() {
        return R.ok(knowledge.publicDocuments());
    }

    @PostMapping
    public R<KnowledgeService.Status> publish(@RequestParam MultipartFile file) {
        long owner = -UserContext.getUid();
        var uploaded = pdfService.upload(owner, file);
        return R.ok(knowledge.enqueuePdf(pdfs.selectById(uploaded.getDocId()), "PUBLIC"));
    }

    @DeleteMapping("/{id}")
    public R<Void> withdraw(@PathVariable long id) {
        knowledge.withdraw(id);
        return R.ok();
    }

    @PostMapping("/{id}/reindex")
    public R<KnowledgeService.Status> reindex(@PathVariable long id) {
        return R.ok(knowledge.reindexPublic(id));
    }
}
