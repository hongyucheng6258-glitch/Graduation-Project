CREATE TABLE IF NOT EXISTS agent_action (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    activity_title VARCHAR(64) NOT NULL,
    remark VARCHAR(255) NOT NULL DEFAULT '',
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    expires_at DATETIME NOT NULL,
    confirmed_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_agent_action_user (user_id, created_at),
    KEY idx_agent_action_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
