CREATE TABLE IF NOT EXISTS agent_campus_draft (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    form JSON NOT NULL,
    target_id BIGINT NULL,
    target_title VARCHAR(128) NULL,
    submitted_id BIGINT NULL,
    expires_at DATETIME NOT NULL,
    KEY idx_agent_campus_draft_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
