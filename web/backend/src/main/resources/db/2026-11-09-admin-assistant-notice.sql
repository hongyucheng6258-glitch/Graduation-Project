CREATE TABLE IF NOT EXISTS admin_assistant_notice_draft (
    id CHAR(36) PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    title VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    expires_at DATETIME NOT NULL,
    notice_id BIGINT DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_admin_notice_draft_owner (admin_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
