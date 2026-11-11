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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PdfDocumentMapper pdfs;
    private final KnowledgeVectorClient vectors;
    private final PdfChunker chunker;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean working = new AtomicBoolean();

    KnowledgeService(JdbcTemplate jdbc, TransactionTemplate transaction, PdfDocumentMapper pdfs,
                     KnowledgeVectorClient vectors,
                     @Value("${campus.agent.rag.chunk-chars:640}") int chunkChars) {
        this.jdbc = jdbc;
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
            jdbc.update("""
                    INSERT IGNORE INTO kb_document(pdf_id,owner_id,scope,state) VALUES(?,?,?,'QUEUED')
                    """, pdf.getId(), pdf.getUserId(), scope);
            Status current = jdbc.query("""
                    SELECT id,pdf_id,state,error_message,active_version,scope,owner_id
                    FROM kb_document WHERE pdf_id=? FOR UPDATE
                    """, this::statusRow, pdf.getId()).get(0);
            if (!scope.equals(current.scope()) || List.of("DELETED", "PURGED").contains(current.state())) {
                throw new BizException(ResultCode.NOT_FOUND, "该文档不可索引");
            }
            if ("INDEXING".equals(current.state()) || ("READY".equals(current.state()) && !rebuild)) {
                return current;
            }
            if ("QUEUED".equals(current.state()) && Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM kb_index_job WHERE document_id=?)", Boolean.class, current.id()))) {
                return current;
            }
            jdbc.update("UPDATE kb_document SET state='QUEUED',error_message=NULL WHERE id=?", current.id());
            jdbc.update("""
                    INSERT INTO kb_index_job(document_id,state) VALUES(?,'QUEUED')
                    ON DUPLICATE KEY UPDATE state='QUEUED',attempts=0,attempt_token=NULL,available_at=NOW()
                    """, current.id());
            return new Status(current.id(), current.pdfId(), "QUEUED", null, current.activeVersion(), current.scope(), current.ownerId());
        });
    }

    public Status status(long id, Long userId) {
        Status status = jdbc.query("""
                SELECT id,pdf_id,state,error_message,active_version,scope,owner_id
                FROM kb_document WHERE id=? AND state NOT IN ('DELETED','PURGED')
                AND ((scope='PRIVATE' AND owner_id=?) OR (scope='PUBLIC' AND active_version IS NOT NULL))
                """, this::statusRow, id, userId).stream().findFirst().orElse(null);
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
            var rows = jdbc.query("""
                    SELECT d.id,p.file_name,c.page_number,c.body,c.version FROM kb_chunk c
                    JOIN kb_document d ON d.id=c.document_id AND d.active_version=c.version
                    JOIN pdf_document p ON p.id=d.pdf_id AND p.user_id=d.owner_id
                    WHERE c.id=? AND d.state NOT IN ('DELETED','PURGED')
                        AND ((?=1 AND d.scope='ADMIN') OR
                            (?=0 AND (d.scope='PUBLIC' OR (d.scope='PRIVATE' AND d.owner_id=?))))
                    """, (rs, row) -> new Evidence(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5)),
                    id, internal ? 1 : 0, internal ? 1 : 0, userId);
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
        var doc = jdbc.query("""
                SELECT id,pdf_id,state,error_message,active_version,scope,owner_id
                FROM kb_document WHERE id=? AND scope='PUBLIC' AND state NOT IN ('DELETED','PURGED')
                """, this::statusRow, id).stream().findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "未找到公共资料"));
        return file(doc);
    }

    FileContent internalFile(long id, String version) {
        return file(verifyInternalVersion(id, version));
    }

    Status verifyInternalVersion(long id, String version) {
        var doc = jdbc.query("""
                SELECT id,pdf_id,state,error_message,active_version,scope,owner_id
                FROM kb_document WHERE id=? AND scope='ADMIN' AND active_version=?
                    AND state NOT IN ('DELETED','PURGED')
                """, this::statusRow, id, version).stream().findFirst()
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
        var pdfId = jdbc.queryForList("""
                SELECT pdf_id FROM kb_document WHERE id=? AND scope=? AND state NOT IN ('DELETED','PURGED')
                """, Long.class, id, scope).stream().findFirst()
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
        return jdbc.query("""
                SELECT d.id,p.file_name,d.state,d.error_message FROM kb_document d
                JOIN pdf_document p ON p.id=d.pdf_id
                WHERE d.scope=? AND d.state NOT IN ('DELETED','PURGED')
                ORDER BY d.id DESC LIMIT 100
                """, (rs, row) -> new PublicDocument(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), scope);
    }

    void remove(long id, Long userId, String scope) {
        transaction.executeWithoutResult(tx -> {
            int changed = jdbc.update("""
                    UPDATE kb_document SET state='DELETED',active_version=NULL,error_message=NULL
                    WHERE id=? AND scope=? AND (? IS NULL OR owner_id=?) AND state NOT IN ('DELETED','PURGED')
                    """, id, scope, userId, userId);
            if (changed == 0) {
                throw new BizException(ResultCode.NOT_FOUND, "未找到知识库文档");
            }
            jdbc.update("DELETE FROM kb_index_job WHERE document_id=?", id);
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
            var ids = jdbc.queryForList("""
                    SELECT document_id FROM kb_index_job
                    WHERE state IN ('QUEUED','RUNNING') AND available_at<=NOW()
                    ORDER BY available_at LIMIT 1
                    """, Long.class);
            if (ids.isEmpty() || !lockDocument(ids.get(0))) {
                return null;
            }
            var waiting = jdbc.query("""
                    SELECT document_id,attempts FROM kb_index_job
                    WHERE document_id=? AND state IN ('QUEUED','RUNNING') AND available_at<=NOW() FOR UPDATE
                    """, (rs, row) -> new Job(rs.getLong(1), rs.getInt(2) + 1, UUID.randomUUID().toString()), ids.get(0));
            if (waiting.isEmpty()) {
                return null;
            }
            Job job = waiting.get(0);
            if (job.attempt() > 3) {
                jdbc.update("UPDATE kb_index_job SET state='FAILED' WHERE document_id=?", job.id());
                jdbc.update("UPDATE kb_document SET state='FAILED',error_message='索引任务超时' WHERE id=? AND state<>'DELETED'", job.id());
                return null;
            }
            jdbc.update("""
                    UPDATE kb_index_job SET state='RUNNING',attempts=?,attempt_token=?,
                    available_at=DATE_ADD(NOW(),INTERVAL 20 MINUTE) WHERE document_id=?
                    """, job.attempt(), job.version(), job.id());
            jdbc.update("UPDATE kb_document SET state='INDEXING' WHERE id=? AND state<>'DELETED'", job.id());
            return job;
        });
    }

    private void index(Job job) {
        try {
            Status doc = jdbc.query("""
                    SELECT id,pdf_id,state,error_message,active_version,scope,owner_id
                    FROM kb_document WHERE id=? AND state='INDEXING'
                    """, this::statusRow, job.id()).stream().findFirst().orElse(null);
            if (doc == null) {
                return;
            }
            var parts = chunker.split(fileBytes(pdfs.selectById(doc.pdfId())));
            for (int start = 0; start < parts.size(); start += BATCH_SIZE) {
                var batch = parts.subList(start, Math.min(parts.size(), start + BATCH_SIZE));
                var embeddings = vectors.embed(batch.stream().map(PdfChunker.Part::text).toList());
                List<String> ids = batch.stream().map(part -> UUID.randomUUID().toString()).toList();
                List<Object[]> rows = new ArrayList<>();
                for (int i = 0; i < batch.size(); i++) {
                    rows.add(new Object[]{ids.get(i), job.id(), job.version(), batch.get(i).page(), batch.get(i).text()});
                }
                jdbc.batchUpdate("INSERT INTO kb_chunk(id,document_id,version,page_number,body) VALUES(?,?,?,?,?)", rows);
                vectors.upsert(doc.id(), doc.ownerId(), doc.scope(), job.version(), ids, embeddings);
            }
            Boolean published = transaction.execute(tx -> {
                if (!lockDocument(job.id())) {
                    return false;
                }
                int claimed = jdbc.update("UPDATE kb_index_job SET state='DONE' WHERE document_id=? AND attempt_token=?",
                        job.id(), job.version());
                return claimed == 1 && jdbc.update("""
                        UPDATE kb_document SET state='READY',active_version=?,error_message=NULL
                        WHERE id=? AND state='INDEXING'
                        """, job.version(), job.id()) == 1;
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
                int changed = jdbc.update("""
                        UPDATE kb_index_job SET state=?,available_at=DATE_ADD(NOW(),INTERVAL 8 SECOND)
                        WHERE document_id=? AND attempt_token=?
                        """, retry ? "QUEUED" : "FAILED", job.id(), job.version());
                if (changed == 1) {
                    jdbc.update("UPDATE kb_document SET state=?,error_message=? WHERE id=? AND state<>'DELETED'",
                            retry ? "QUEUED" : "FAILED", e instanceof BizException ? e.getMessage() : "PDF 索引失败",
                            job.id());
                }
            });
            log.warn("知识库文档 {} 索引失败: {}", job.id(), e.getClass().getSimpleName());
        }
    }

    private boolean lockDocument(long id) {
        return !jdbc.queryForList("""
                SELECT id FROM kb_document WHERE id=? AND state NOT IN ('DELETED','PURGED') FOR UPDATE
                """, Long.class, id).isEmpty();
    }

    private void cleanVersion(long id, String version) {
        try {
            vectors.delete(id, version);
            jdbc.update("DELETE FROM kb_chunk WHERE document_id=? AND version=?", id, version);
        } catch (RuntimeException e) {
            log.warn("知识库文档 {} 的未发布索引未能清理", id);
        }
    }

    private void purgeDeleted() {
        var ids = jdbc.queryForList("SELECT id FROM kb_document WHERE state='DELETED' ORDER BY id LIMIT 1", Long.class);
        if (!ids.isEmpty()) {
            long id = ids.get(0);
            vectors.delete(id, null);
            jdbc.update("DELETE FROM kb_chunk WHERE document_id=?", id);
            jdbc.update("UPDATE kb_document SET state='PURGED' WHERE id=? AND state='DELETED'", id);
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

    private Status statusRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Status(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getLong(7));
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
