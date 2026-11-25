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

/**
 * KnowledgeService 服务接口。
 */
public interface IKnowledgeService {

    record Status(long id, long pdfId, String state, String error, @JsonIgnore String activeVersion,
                         String scope, @JsonIgnore long ownerId) {}

    record Evidence(long documentId, String fileName, int page, String text, String version) {}

    record PublicDocument(long id, String fileName, String state, String error) {}

    record FileContent(String fileName, byte[] bytes) {}

    Status enqueue(Long userId, long pdfId, boolean rebuild);

    Status status(long id, Long userId);

    List<Evidence> search(Long userId, Long docId, String question);

    List<Evidence> search(Long userId, Long docId, String question, long deadline);

    List<Evidence> searchInternal(String question, long deadline);

    void verifyVersion(Long uid, long id, String version);

    Status verifyInternalVersion(long id, String version);

    FileContent file(long id, Long userId);

    void delete(long id, Long userId);

    List<PublicDocument> publicDocuments();

    List<PublicDocument> documents(String scope);

    Status enqueuePdf(PdfDocument pdf, String scope);

    FileContent publicFile(long id);

    FileContent internalFile(long id, String version);

    void withdraw(long id);

    Status reindexPublic(long id);

    Status reindexScoped(long id, String scope);

    void remove(long id, Long userId, String scope);
}
