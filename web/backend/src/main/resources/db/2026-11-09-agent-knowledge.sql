-- Older installations stored MinIO URLs in a short varchar.
ALTER TABLE pdf_document
    MODIFY COLUMN file_url LONGTEXT NOT NULL COMMENT 'Base64 Data URI or legacy file location';

CREATE TABLE IF NOT EXISTS kb_document (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    pdf_id BIGINT NOT NULL UNIQUE,
    owner_id BIGINT NOT NULL,
    scope VARCHAR(10) NOT NULL DEFAULT 'PRIVATE',
    state VARCHAR(16) NOT NULL,
    active_version CHAR(36) DEFAULT NULL,
    error_message VARCHAR(200) DEFAULT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_chunk (
    id CHAR(36) NOT NULL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    version CHAR(36) NOT NULL,
    page_number INT NOT NULL,
    body TEXT NOT NULL,
    KEY idx_kb_chunk_version (document_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_index_job (
    document_id BIGINT NOT NULL PRIMARY KEY,
    state VARCHAR(12) NOT NULL,
    attempt_token CHAR(36) DEFAULT NULL,
    attempts INT NOT NULL DEFAULT 0,
    available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_kb_job_poll (state, available_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
