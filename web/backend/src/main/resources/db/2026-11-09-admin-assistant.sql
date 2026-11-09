CREATE TABLE IF NOT EXISTS admin_assistant_session (
    id CHAR(36) PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    title VARCHAR(64) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_admin_assistant_sessions (admin_id, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_assistant_turn (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    body JSON NOT NULL,
    KEY idx_admin_assistant_turns (session_id, id),
    CONSTRAINT fk_admin_assistant_session FOREIGN KEY (session_id)
        REFERENCES admin_assistant_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
