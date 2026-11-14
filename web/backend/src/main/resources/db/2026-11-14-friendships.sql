CREATE TABLE IF NOT EXISTS friendship (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user1_id BIGINT NOT NULL,
    user2_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    greeting VARCHAR(80) NOT NULL DEFAULT '',
    status VARCHAR(12) NOT NULL,
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_friendship_pair (user1_id, user2_id),
    KEY idx_friendship_user2 (user2_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
