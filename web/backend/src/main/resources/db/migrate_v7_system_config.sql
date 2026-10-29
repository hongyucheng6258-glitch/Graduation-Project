-- ============================================================
-- 通用系统配置表（管理端在线修改，立即生效，免重启）
-- 执行方式：mysql -uroot -p ai_campus_platform < migrate_v7_system_config.sql
-- 字符集：文件与连接统一 utf8mb4（中文/emoji 不丢失）
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `system_config` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `config_key`   VARCHAR(64)  NOT NULL COMMENT '配置键',
  `config_value` TEXT         COMMENT '配置值（文本型，支持长内容/JSON/逗号分隔列表）',
  `value_type`   VARCHAR(16)  NOT NULL DEFAULT 'string' COMMENT '值类型：string/int/long/double/bool/json/list',
  `description`  VARCHAR(255) DEFAULT NULL COMMENT '配置说明',
  `category`     VARCHAR(32)  NOT NULL DEFAULT 'basic' COMMENT '分组：basic/site/security/audit/chat/upload',
  `sort`         INT          NOT NULL DEFAULT 0 COMMENT '同组内排序',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用系统配置表（热更新）';

-- 预置系统配置项。
-- 修复说明（P1）：此前使用 ON DUPLICATE KEY UPDATE 会「覆盖」config_value，
-- 重复执行迁移会把管理端在线保存的值（维护开关、上传限制、站点名称等）重置回默认值。
-- 现改为 INSERT IGNORE：仅当 config_key 不存在时插入默认值；
-- 已存在的记录（含用户修改过的值）保持原样，绝不覆盖。
INSERT IGNORE INTO `system_config` (`config_key`, `config_value`, `value_type`, `description`, `category`, `sort`) VALUES
-- 站点基础
('site_name', 'AI校园综合服务平台', 'string', '系统名称', 'site', 1),
('site_slogan', '智慧校园，一站式服务', 'string', '系统标语/副标题', 'site', 2),
('maintenance_mode', 'false', 'bool', '维护模式开关（开启后学生端仅展示维护提示）', 'basic', 10),
('register_enabled', 'true', 'bool', '是否开放学生注册', 'basic', 11),
('post_publish_enabled', 'true', 'bool', '是否允许学生发布动态', 'basic', 12),
('idle_publish_enabled', 'true', 'bool', '是否允许发布闲置物品', 'basic', 13),
('activity_publish_enabled', 'true', 'bool', '是否允许发布活动', 'basic', 14),
-- 内容审核
('audit_high_risk_words', '转账,押金,银行卡,刷单,兼职返利,加微信,二维码,代充,账号交易', 'list', '内容审核高风险词（逗号分隔，命中即拦截）', 'audit', 1),
('audit_medium_risk_words', '悬赏,收费,校外,联系我,手机号,群聊,购买', 'list', '内容审核中风险词（逗号分隔，命中转人工）', 'audit', 2),
('ai_audit_enabled', 'false', 'bool', '是否启用大模型内容审核（关闭时仅用本地规则）', 'audit', 3),
-- 聊天/私信限流
('chat_rate_per_sec', '5', 'int', '私信每秒发送上限', 'chat', 1),
('chat_rate_per_min', '100', 'int', '私信每分钟发送上限', 'chat', 2),
('chat_message_max_length', '2000', 'int', '单条私信最大字符数', 'chat', 3),
-- 上传
('upload_max_size_mb', '20', 'int', '单文件最大上传大小(MB)', 'upload', 1),
('upload_image_max_count', '9', 'int', '单次最多上传图片数量', 'upload', 2),
-- 安全
('user_default_status', '0', 'int', '新用户默认状态（0正常 1禁用）', 'security', 1),
('login_fail_lock_threshold', '5', 'int', '连续登录失败锁定阈值（0=不锁定）', 'security', 2);
