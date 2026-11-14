CREATE TABLE IF NOT EXISTS verified_student_phone (
  user_id BIGINT PRIMARY KEY,
  phone CHAR(11) NOT NULL UNIQUE,
  verified_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS student_password_recovery (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_hash CHAR(64) NOT NULL UNIQUE,
  student_no VARCHAR(32) NOT NULL,
  claimed_name VARCHAR(64) NOT NULL,
  contact VARCHAR(80) NOT NULL,
  explanation VARCHAR(500) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  decision_reason VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  decided_at DATETIME,
  KEY idx_recovery_status (status,id)
);
