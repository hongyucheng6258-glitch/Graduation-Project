CREATE TABLE IF NOT EXISTS activity_waitlist (
  id BIGINT NOT NULL AUTO_INCREMENT,
  activity_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  state VARCHAR(16) NOT NULL,
  queued_at DATETIME NOT NULL,
  offer_until DATETIME DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_waitlist_activity_user (activity_id, user_id),
  KEY idx_waitlist_queue (activity_id, state, id),
  KEY idx_waitlist_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS idle_handoff (
  appointment_id BIGINT NOT NULL,
  version INT NOT NULL DEFAULT 0,
  proposed_by BIGINT DEFAULT NULL,
  meeting_at DATETIME DEFAULT NULL,
  location VARCHAR(128) DEFAULT NULL,
  confirmed TINYINT NOT NULL DEFAULT 0,
  buyer_done TINYINT NOT NULL DEFAULT 0,
  seller_done TINYINT NOT NULL DEFAULT 0,
  reminder_sent TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (appointment_id),
  KEY idx_idle_handoff_reminder (confirmed, reminder_sent, meeting_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
