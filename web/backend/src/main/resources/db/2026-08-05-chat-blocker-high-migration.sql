USE ai_campus_platform;

CREATE TABLE IF NOT EXISTS `upload_resource` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `owner_user_id` BIGINT NOT NULL,
  `resource_url` VARCHAR(500) NOT NULL,
  `resource_type` VARCHAR(16) NOT NULL COMMENT 'image/file',
  `content_type` VARCHAR(128) DEFAULT NULL,
  `file_size` BIGINT NOT NULL DEFAULT 0,
  `biz_type` VARCHAR(32) DEFAULT NULL,
  `biz_id` BIGINT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_upload_resource_url` (`resource_url`),
  KEY `idx_upload_owner` (`owner_user_id`, `resource_type`),
  KEY `idx_upload_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传资源归属与消费记录';

SET @has_private_key_column = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'message' AND COLUMN_NAME = 'private_unread_key'
);
SET @sql = IF(@has_private_key_column = 0,
  'ALTER TABLE `message` ADD COLUMN `private_unread_key` VARCHAR(96) GENERATED ALWAYS AS (CASE WHEN `type` = ''private_message'' AND `is_read` = 0 THEN CONCAT(`user_id`, '':'', `biz_type`, '':'', `biz_id`) ELSE NULL END) STORED',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DELETE stale FROM `message` stale
JOIN `message` keeper
  ON stale.user_id = keeper.user_id
 AND stale.biz_type = keeper.biz_type
 AND stale.biz_id = keeper.biz_id
 AND stale.type = 'private_message'
 AND keeper.type = 'private_message'
 AND stale.is_read = 0
 AND keeper.is_read = 0
 AND stale.id > keeper.id;

SET @has_private_unique = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'message' AND INDEX_NAME = 'uk_private_unread_aggregate'
);
SET @sql = IF(@has_private_unique = 0,
  'ALTER TABLE `message` ADD UNIQUE KEY `uk_private_unread_aggregate` (`private_unread_key`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
