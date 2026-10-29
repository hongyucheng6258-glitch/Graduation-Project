-- ============================================================
-- v6：所有新上传资源存入数据库
-- 新资源保存为 Base64 Data URI；历史 MinIO URL 不迁移、不修改，继续兼容读取。
-- ============================================================

ALTER TABLE `upload_resource`
  MODIFY COLUMN `resource_url` LONGTEXT NOT NULL
  COMMENT 'Base64 Data URI 或历史 MinIO 地址';

ALTER TABLE `pdf_document`
  MODIFY COLUMN `file_url` LONGTEXT NOT NULL
  COMMENT 'Base64 Data URI 或历史 MinIO 地址';
