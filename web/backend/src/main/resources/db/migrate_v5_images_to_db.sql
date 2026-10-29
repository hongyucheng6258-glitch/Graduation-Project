-- ============================================================
-- v5：图片直接存入数据库（base64 data URI）
-- 说明：毕业设计演示环境不依赖 MinIO 可达性/端口/域名，
--       上传接口改为返回 data:image/...;base64,... ，
--       业务表图片字段统一扩为 MEDIUMTEXT（16MB），
--       并移除 upload_resource.resource_url 的唯一索引（同图 base64 相同）。
-- 已有库执行本脚本即可；新库 schema.sql 已包含同等结构。
-- ============================================================

ALTER TABLE `user` MODIFY COLUMN `avatar` MEDIUMTEXT DEFAULT NULL COMMENT '头像（base64 data URI）';
ALTER TABLE `admin` MODIFY COLUMN `avatar` MEDIUMTEXT DEFAULT NULL;

ALTER TABLE `ai_message` MODIFY COLUMN `content` MEDIUMTEXT NOT NULL COMMENT '消息内容（图片消息为 base64）';

ALTER TABLE `wrong_question` MODIFY COLUMN `question_image` MEDIUMTEXT DEFAULT NULL COMMENT '题目图片（base64 data URI）';

ALTER TABLE `idle_item` MODIFY COLUMN `images` MEDIUMTEXT DEFAULT NULL COMMENT '图片 JSON 数组（base64 data URI）';

ALTER TABLE `activity` MODIFY COLUMN `images` MEDIUMTEXT DEFAULT NULL COMMENT '图片 JSON 数组（base64 data URI）';

ALTER TABLE `lost_found` MODIFY COLUMN `images` MEDIUMTEXT DEFAULT NULL;

ALTER TABLE `notice` MODIFY COLUMN `cover` MEDIUMTEXT DEFAULT NULL COMMENT '封面图（base64 data URI）';

ALTER TABLE `post` MODIFY COLUMN `images` MEDIUMTEXT DEFAULT NULL;

ALTER TABLE `upload_resource` DROP INDEX `uk_upload_resource_url`;
ALTER TABLE `upload_resource` MODIFY COLUMN `resource_url` MEDIUMTEXT NOT NULL;

ALTER TABLE `chat_message` MODIFY COLUMN `content` MEDIUMTEXT NOT NULL;
