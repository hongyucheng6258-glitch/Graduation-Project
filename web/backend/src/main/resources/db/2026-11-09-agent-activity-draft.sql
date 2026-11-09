CREATE TABLE IF NOT EXISTS agent_activity_draft (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(64) NOT NULL,
    description TEXT DEFAULT NULL,
    category VARCHAR(32) DEFAULT NULL,
    location VARCHAR(128) DEFAULT NULL,
    start_time DATETIME DEFAULT NULL,
    end_time DATETIME DEFAULT NULL,
    signup_deadline DATETIME DEFAULT NULL,
    max_members INT DEFAULT NULL,
    expires_at DATETIME NOT NULL,
    activity_id BIGINT DEFAULT NULL,
    confirmation_hash CHAR(64) DEFAULT NULL,
    KEY idx_activity_draft_owner (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
