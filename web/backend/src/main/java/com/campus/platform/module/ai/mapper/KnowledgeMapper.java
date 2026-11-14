package com.campus.platform.module.ai.mapper;

import com.campus.platform.module.ai.rag.KnowledgeService.Evidence;
import com.campus.platform.module.ai.rag.KnowledgeService.PublicDocument;
import com.campus.platform.module.ai.rag.KnowledgeService.Status;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface KnowledgeMapper {
    @Insert("""
            INSERT IGNORE INTO kb_document(pdf_id,owner_id,scope,state) VALUES(#{pdfId},#{ownerId},#{scope},'QUEUED')
            """)
    int insertDocument(@Param("pdfId") Long pdfId, @Param("ownerId") Long ownerId, @Param("scope") String scope);

    @Select("""
            SELECT id,pdf_id,state,error_message,active_version,scope,owner_id
            FROM kb_document WHERE pdf_id=#{pdfId} FOR UPDATE
            """)
    List<Status> lockByPdf(@Param("pdfId") Long pdfId);

    @Select("SELECT EXISTS(SELECT 1 FROM kb_index_job WHERE document_id=#{id})")
    Boolean jobExists(@Param("id") long id);

    @Update("UPDATE kb_document SET state='QUEUED',error_message=NULL WHERE id=#{id}")
    int queueDocument(@Param("id") long id);

    @Insert("""
            INSERT INTO kb_index_job(document_id,state) VALUES(#{id},'QUEUED')
            ON DUPLICATE KEY UPDATE state='QUEUED',attempts=0,attempt_token=NULL,available_at=NOW()
            """)
    int queueJob(@Param("id") long id);

    @Select("""
            SELECT id,pdf_id,state,error_message,active_version,scope,owner_id
            FROM kb_document WHERE id=#{id} AND state NOT IN ('DELETED','PURGED')
            AND ((scope='PRIVATE' AND owner_id=#{userId,jdbcType=BIGINT}) OR (scope='PUBLIC' AND active_version IS NOT NULL))
            """)
    List<Status> findVisible(@Param("id") long id, @Param("userId") Long userId);

    @Select("""
            SELECT d.id,p.file_name,c.page_number,c.body,c.version FROM kb_chunk c
            JOIN kb_document d ON d.id=c.document_id AND d.active_version=c.version
            JOIN pdf_document p ON p.id=d.pdf_id AND p.user_id=d.owner_id
            WHERE c.id=#{id} AND d.state NOT IN ('DELETED','PURGED')
                AND ((#{internal}=1 AND d.scope='ADMIN') OR
                    (#{internal}=0 AND (d.scope='PUBLIC' OR (d.scope='PRIVATE' AND d.owner_id=#{userId,jdbcType=BIGINT}))))
            """)
    List<Evidence> findEvidence(@Param("id") String id, @Param("internal") int internal, @Param("userId") Long userId);

    @Select("""
            SELECT id,pdf_id,state,error_message,active_version,scope,owner_id
            FROM kb_document WHERE id=#{id} AND scope='PUBLIC' AND state NOT IN ('DELETED','PURGED')
            """)
    List<Status> findPublic(@Param("id") long id);

    @Select("""
            SELECT id,pdf_id,state,error_message,active_version,scope,owner_id
            FROM kb_document WHERE id=#{id} AND scope='ADMIN' AND active_version=#{version}
                AND state NOT IN ('DELETED','PURGED')
            """)
    List<Status> findInternal(@Param("id") long id, @Param("version") String version);

    @Select("""
            SELECT pdf_id FROM kb_document WHERE id=#{id} AND scope=#{scope} AND state NOT IN ('DELETED','PURGED')
            """)
    List<Long> findPdfId(@Param("id") long id, @Param("scope") String scope);

    @Select("""
            SELECT d.id,p.file_name,d.state,d.error_message FROM kb_document d
            JOIN pdf_document p ON p.id=d.pdf_id
            WHERE d.scope=#{scope} AND d.state NOT IN ('DELETED','PURGED')
            ORDER BY d.id DESC LIMIT 100
            """)
    List<PublicDocument> findDocuments(@Param("scope") String scope);

    @Update("""
            UPDATE kb_document SET state='DELETED',active_version=NULL,error_message=NULL
            WHERE id=#{id} AND scope=#{scope} AND (#{userId,jdbcType=BIGINT} IS NULL OR owner_id=#{userId,jdbcType=BIGINT}) AND state NOT IN ('DELETED','PURGED')
            """)
    int markDeleted(@Param("id") long id, @Param("scope") String scope, @Param("userId") Long userId);

    @Delete("DELETE FROM kb_index_job WHERE document_id=#{id}")
    int deleteJob(@Param("id") long id);

    @Select("""
            SELECT document_id FROM kb_index_job
            WHERE state IN ('QUEUED','RUNNING') AND available_at<=NOW()
            ORDER BY available_at LIMIT 1
            """)
    List<Long> findAvailable();

    @Select("""
            SELECT document_id,attempts FROM kb_index_job
            WHERE document_id=#{id} AND state IN ('QUEUED','RUNNING') AND available_at<=NOW() FOR UPDATE
            """)
    List<JobRow> lockAvailableJob(@Param("id") long id);

    @Update("UPDATE kb_index_job SET state='FAILED' WHERE document_id=#{id}")
    int failJob(@Param("id") long id);

    @Update("UPDATE kb_document SET state='FAILED',error_message='索引任务超时' WHERE id=#{id} AND state<>'DELETED'")
    int timeoutDocument(@Param("id") long id);

    @Update("""
            UPDATE kb_index_job SET state='RUNNING',attempts=#{attempt},attempt_token=#{version},
            available_at=DATE_ADD(NOW(),INTERVAL 20 MINUTE) WHERE document_id=#{id}
            """)
    int startJob(@Param("attempt") int attempt, @Param("version") String version, @Param("id") long id);

    @Update("UPDATE kb_document SET state='INDEXING' WHERE id=#{id} AND state<>'DELETED'")
    int startDocument(@Param("id") long id);

    @Select("""
            SELECT id,pdf_id,state,error_message,active_version,scope,owner_id
            FROM kb_document WHERE id=#{id} AND state='INDEXING'
            """)
    List<Status> findIndexing(@Param("id") long id);

    @Update("UPDATE kb_index_job SET state='DONE' WHERE document_id=#{id} AND attempt_token=#{version}")
    int finishJob(@Param("id") long id, @Param("version") String version);

    @Update("""
            UPDATE kb_document SET state='READY',active_version=#{version},error_message=NULL
            WHERE id=#{id} AND state='INDEXING'
            """)
    int publishDocument(@Param("version") String version, @Param("id") long id);

    @Update("""
            UPDATE kb_index_job SET state=#{state},available_at=DATE_ADD(NOW(),INTERVAL 8 SECOND)
            WHERE document_id=#{id} AND attempt_token=#{version}
            """)
    int retryJob(@Param("state") String state, @Param("id") long id, @Param("version") String version);

    @Update("UPDATE kb_document SET state=#{state},error_message=#{error} WHERE id=#{id} AND state<>'DELETED'")
    int updateDocumentError(@Param("state") String state, @Param("error") String error, @Param("id") long id);

    @Select("""
            SELECT id FROM kb_document WHERE id=#{id} AND state NOT IN ('DELETED','PURGED') FOR UPDATE
            """)
    List<Long> lockDocument(@Param("id") long id);

    @Delete("DELETE FROM kb_chunk WHERE document_id=#{id} AND version=#{version}")
    int deleteVersion(@Param("id") long id, @Param("version") String version);

    @Select("SELECT id FROM kb_document WHERE state='DELETED' ORDER BY id LIMIT 1")
    List<Long> findDeleted();

    @Delete("DELETE FROM kb_chunk WHERE document_id=#{id}")
    int deleteChunks(@Param("id") long id);

    @Update("UPDATE kb_document SET state='PURGED' WHERE id=#{id} AND state='DELETED'")
    int markPurged(@Param("id") long id);

    @Insert("""
            <script>
            INSERT INTO kb_chunk(id,document_id,version,page_number,body) VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.id},#{row.documentId},#{row.version},#{row.page},#{row.body})
            </foreach>
            </script>
            """)
    int insertChunks(@Param("rows") List<ChunkRow> rows);

    record JobRow(long documentId, int attempts) {}

    record ChunkRow(String id, long documentId, String version, int page, String body) {}
}
