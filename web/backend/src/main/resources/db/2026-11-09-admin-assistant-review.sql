CREATE TABLE IF NOT EXISTS admin_assistant_review (
    id CHAR(36) PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    admin_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    item_title VARCHAR(64) NOT NULL,
    item_excerpt VARCHAR(480) DEFAULT NULL,
    item_version CHAR(64) NOT NULL,
    decision VARCHAR(8) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    applied_at DATETIME(6) DEFAULT NULL,
    audit_status TINYINT DEFAULT NULL,
    KEY idx_admin_assistant_review_session (admin_id, session_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
