package com.campus.platform.module.ai.rag;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.entity.PdfDocument;
import com.campus.platform.module.ai.mapper.PdfDocumentMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import com.campus.platform.module.ai.mapper.KnowledgeMapper;
import com.campus.platform.module.ai.mapper.KnowledgeMapper.ChunkRow;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@ConditionalOnProperty(name = "campus.agent.rag.enabled", havingValue = "true")
public class KnowledgeService {
    private static final int BATCH_SIZE = 16;
    private final KnowledgeMapper knowledge;
    private final TransactionTemplate transaction;
    private final PdfDocumentMapper pdfs;
    private final KnowledgeVectorClient vectors;
    private final PdfChunker chunker;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean working = new AtomicBoolean();

    KnowledgeService(KnowledgeMapper knowledge, TransactionTemplate transaction, PdfDocumentMapper pdfs,
                     KnowledgeVectorClient vectors,
                     @Value("${campus.agent.rag.chunk-chars:640}") int chunkChars) {
        this.knowledge = knowledge;
        this.transaction = transaction;
        this.pdfs = pdfs;
        this.vectors = vectors;
        this.chunker = new PdfChunker(chunkChars);
    }

    public Status enqueue(Long userId, long pdfId, boolean rebuild) {
        PdfDocument pdf = pdfs.selectById(pdfId);
        if (userId == null || pdf == null || !userId.equals(pdf.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "未找到该 PDF");
        }
        if (!Objects.equals(pdf.getStatus(), 1)) {
            throw new BizException(ResultCode.BAD_REQUEST, "PDF 尚未成功提取文字");
        }
        return enqueuePdf(pdf, "PRIVATE", rebuild);
    }

    Status enqueuePdf(PdfDocument pdf, String scope) {
        return enqueuePdf(pdf, scope, false);
    }

    private Status enqueuePdf(PdfDocument pdf, String scope, boolean rebuild) {
        return transaction.execute(tx -> {
            knowledge.insertDocument(pdf.getId(), pdf.getUserId(), scope);
            Status current = knowledge.lockByPdf(pdf.getId()).get(0);
            if (!scope.equals(current.scope()) || List.of("DELETED", "PURGED").contains(current.state())) {
                throw new BizException(ResultCode.NOT_FOUND, "该文档不可索引");
            }
            if ("INDEXING".equals(current.state()) || ("READY".equals(current.state()) && !rebuild)) {
                return current;
            }
            if ("QUEUED".equals(current.state()) && Boolean.TRUE.equals(knowledge.jobExists(current.id()))) {
                return current;
            }
            knowledge.queueDocument(current.id());
            knowledge.queueJob(current.id());
            return new Status(current.id(), current.pdfId(), "QUEUED", null, current.activeVersion(), current.scope(), current.ownerId());
        });
    }

    public Status status(long id, Long userId) {
        Status status = knowledge.findVisible(id, userId).stream().findFirst().orElse(null);
        if (status == null || userId == null) {
            throw new BizException(ResultCode.NOT_FOUND, "未找到知识库文档");
        }
        return status;
    }

    public List<Evidence> search(Long userId, Long docId, String question) {
        return search(userId, docId, question, System.nanoTime() + Duration.ofSeconds(25).toNanos());
    }

    List<Evidence> search(Long userId, Long docId, String question, long deadline) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (docId != null && status(docId, userId).activeVersion() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "文档索引尚未就绪");
        }
        return retrieve(userId, docId, question, deadline, false);
    }

    List<Evidence> searchInternal(String question, long deadline) {
        return retrieve(0L, null, question, deadline, true);
    }

    private List<Evidence> retrieve(Long userId, Long docId, String question, long deadline, boolean internal) {
        var ids = internal ? vectors.search(userId, docId, question, deadline, true)
                : vectors.search(userId, docId, question, deadline);
        List<Evidence> evidence = new ArrayList<>();
        for (String id : ids) {
            if (System.nanoTime() >= deadline) {
                throw new BizException(ResultCode.AI_INVOKE_FAIL, "知识库查询已超时");
            }
            var rows = knowledge.findEvidence(id, internal ? 1 : 0, userId);
            if (!rows.isEmpty()) {
                Evidence candidate = rows.get(0);
                if ((docId == null || docId.equals(candidate.documentId())) && !evidence.contains(candidate)) {
                    evidence.add(candidate);
                }
            }
            if (evidence.size() == 5) {
                break;
            }
        }
        return evidence;
    }

    public void verifyVersion(Long uid, long id, String version) {
        var current = status(id, uid);
        if (version == null || !version.equals(current.activeVersion())) {
            throw new BizException(ResultCode.AI_INVOKE_FAIL, "资料在回答期间更新，请重新提问");
        }
    }

    public FileContent file(long id, Long userId) {
        return file(status(id, userId));
    }

    FileContent publicFile(long id) {
        var doc = knowledge.findPublic(id).stream().findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "未找到公共资料"));
        return file(doc);
    }

    FileContent internalFile(long id, String version) {
        return file(verifyInternalVersion(id, version));
    }

    Status verifyInternalVersion(long id, String version) {
        var doc = knowledge.findInternal(id, version).stream().findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "资料已更新或撤回，请重新查询"));
        return doc;
    }

    private FileContent file(Status doc) {
        PdfDocument pdf = pdfs.selectById(doc.pdfId());
        if (pdf == null || !Objects.equals(pdf.getUserId(), doc.ownerId())) {
            throw new BizException(ResultCode.NOT_FOUND, "未找到 PDF 文件");
        }
        return new FileContent(pdf.getFileName(), fileBytes(pdf));
    }

    public void delete(long id, Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        remove(id, userId, "PRIVATE");
    }

    void withdraw(long id) {
        remove(id, null, "PUBLIC");
    }

    Status reindexPublic(long id) {
        return reindexScoped(id, "PUBLIC");
    }

    Status reindexScoped(long id, String scope) {
        var pdfId = knowledge.findPdfId(id, scope).stream().findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "未找到资料"));
        var pdf = pdfs.selectById(pdfId);
        if (pdf == null) {
            throw new BizException(ResultCode.NOT_FOUND, "未找到公共资料文件");
        }
        return enqueuePdf(pdf, scope, true);
    }

    List<PublicDocument> publicDocuments() {
        return documents("PUBLIC");
    }

    List<PublicDocument> documents(String scope) {
        return knowledge.findDocuments(scope);
    }

    void remove(long id, Long userId, String scope) {
        transaction.executeWithoutResult(tx -> {
            int changed = knowledge.markDeleted(id, scope, userId);
            if (changed == 0) {
                throw new BizException(ResultCode.NOT_FOUND, "未找到知识库文档");
            }
            knowledge.deleteJob(id);
        });
    }

    @Scheduled(fixedDelay = 1200)
    void poll() {
        if (working.compareAndSet(false, true)) {
            worker.execute(() -> {
                try {
                    try {
                        purgeDeleted();
                    } catch (RuntimeException e) {
                        log.warn("知识库清理等待重试: {}", e.getClass().getSimpleName());
                    }
                    Job job = claim();
                    if (job != null) {
                        index(job);
                    }
                } catch (RuntimeException e) {
                    log.warn("知识库任务调度失败: {}", e.getClass().getSimpleName());
                } finally {
                    working.set(false);
                }
            });
        }
    }

    private Job claim() {
        return transaction.execute(tx -> {
            var ids = knowledge.findAvailable();
            if (ids.isEmpty() || !lockDocument(ids.get(0))) {
                return null;
            }
            var waiting = knowledge.lockAvailableJob(ids.get(0));
            if (waiting.isEmpty()) {
                return null;
            }
            var row = waiting.get(0);
            Job job = new Job(row.documentId(), row.attempts() + 1, UUID.randomUUID().toString());
            if (job.attempt() > 3) {
                knowledge.failJob(job.id());
                knowledge.timeoutDocument(job.id());
                return null;
            }
            knowledge.startJob(job.attempt(), job.version(), job.id());
            knowledge.startDocument(job.id());
            return job;
        });
    }

    private void index(Job job) {
        try {
            Status doc = knowledge.findIndexing(job.id()).stream().findFirst().orElse(null);
            if (doc == null) {
                return;
            }
            var parts = chunker.split(fileBytes(pdfs.selectById(doc.pdfId())));
            for (int start = 0; start < parts.size(); start += BATCH_SIZE) {
                var batch = parts.subList(start, Math.min(parts.size(), start + BATCH_SIZE));
                var embeddings = vectors.embed(batch.stream().map(PdfChunker.Part::text).toList());
                List<String> ids = batch.stream().map(part -> UUID.randomUUID().toString()).toList();
                List<ChunkRow> rows = new ArrayList<>();
                for (int i = 0; i < batch.size(); i++) {
                    rows.add(new ChunkRow(ids.get(i), job.id(), job.version(), batch.get(i).page(), batch.get(i).text()));
                }
                knowledge.insertChunks(rows);
                vectors.upsert(doc.id(), doc.ownerId(), doc.scope(), job.version(), ids, embeddings);
            }
            Boolean published = transaction.execute(tx -> {
                if (!lockDocument(job.id())) {
                    return false;
                }
                int claimed = knowledge.finishJob(job.id(), job.version());
                return claimed == 1 && knowledge.publishDocument(job.version(), job.id()) == 1;
            });
            if (!Boolean.TRUE.equals(published)) {
                cleanVersion(job.id(), job.version());
            } else if (doc.activeVersion() != null) {
                cleanVersion(doc.id(), doc.activeVersion());
            }
        } catch (Exception e) {
            cleanVersion(job.id(), job.version());
            boolean retry = job.attempt() < 3 && !(e instanceof BizException b
                    && !ResultCode.AI_INVOKE_FAIL.getCode().equals(b.getCode()));
            transaction.executeWithoutResult(tx -> {
                if (!lockDocument(job.id())) {
                    return;
                }
                int changed = knowledge.retryJob(retry ? "QUEUED" : "FAILED", job.id(), job.version());
                if (changed == 1) {
                    knowledge.updateDocumentError(retry ? "QUEUED" : "FAILED", e instanceof BizException ? e.getMessage() : "PDF 索引失败", job.id());
                }
            });
            log.warn("知识库文档 {} 索引失败: {}", job.id(), e.getClass().getSimpleName());
        }
    }

    private boolean lockDocument(long id) {
        return !knowledge.lockDocument(id).isEmpty();
    }

    private void cleanVersion(long id, String version) {
        try {
            vectors.delete(id, version);
            knowledge.deleteVersion(id, version);
        } catch (RuntimeException e) {
            log.warn("知识库文档 {} 的未发布索引未能清理", id);
        }
    }

    private void purgeDeleted() {
        var ids = knowledge.findDeleted();
        if (!ids.isEmpty()) {
            long id = ids.get(0);
            vectors.delete(id, null);
            knowledge.deleteChunks(id);
            knowledge.markPurged(id);
        }
    }

    private byte[] fileBytes(PdfDocument pdf) {
        String source = pdf == null ? null : pdf.getFileUrl();
        if (source == null || !source.startsWith("data:") || !source.contains(";base64,")) {
            throw new BizException(ResultCode.BAD_REQUEST, "请重新上传 PDF 后再建立索引");
        }
        try {
            return Base64.getDecoder().decode(source.substring(source.indexOf(";base64,") + 8));
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "PDF 文件内容无效");
        }
    }

    @PreDestroy
    void stopWorker() {
        worker.shutdownNow();
    }

    public record Status(long id, long pdfId, String state, String error, @JsonIgnore String activeVersion,
                         String scope, @JsonIgnore long ownerId) {}
    public record Evidence(long documentId, String fileName, int page, String text, String version) {}
    public record PublicDocument(long id, String fileName, String state, String error) {}
    public record FileContent(String fileName, byte[] bytes) {}
    private record Job(long id, int attempt, String version) {}
}
