CREATE TABLE IF NOT EXISTS `admin_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `admin_id` BIGINT NOT NULL,
  `admin_username` VARCHAR(64) NOT NULL,
  `module` VARCHAR(32) NOT NULL,
  `action` VARCHAR(32) NOT NULL,
  `target_type` VARCHAR(32) DEFAULT NULL,
  `target_id` BIGINT DEFAULT NULL,
  `source` VARCHAR(16) NOT NULL,
  `succeeded` TINYINT(1) NOT NULL,
  `result_code` INT NOT NULL,
  `duration_ms` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_admin_op_target` (`target_id`, `module`, `id`),
  KEY `idx_admin_op_actor` (`admin_username`, `id`),
  KEY `idx_admin_op_time` (`create_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理端操作日志';
