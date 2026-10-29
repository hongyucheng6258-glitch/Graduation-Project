ALTER TABLE `idle_item`
  ADD COLUMN `ai_risk_level` TINYINT DEFAULT NULL COMMENT '0低风险 1中风险 2高风险' AFTER `audit_reason`,
  ADD COLUMN `ai_audit_reason` VARCHAR(500) DEFAULT NULL AFTER `ai_risk_level`,
  ADD COLUMN `ai_audit_time` DATETIME DEFAULT NULL AFTER `ai_audit_reason`,
  ADD COLUMN `audit_source` VARCHAR(16) NOT NULL DEFAULT 'manual' AFTER `ai_audit_time`;

ALTER TABLE `activity`
  ADD COLUMN `ai_risk_level` TINYINT DEFAULT NULL COMMENT '0低风险 1中风险 2高风险' AFTER `audit_reason`,
  ADD COLUMN `ai_audit_reason` VARCHAR(500) DEFAULT NULL AFTER `ai_risk_level`,
  ADD COLUMN `ai_audit_time` DATETIME DEFAULT NULL AFTER `ai_audit_reason`,
  ADD COLUMN `audit_source` VARCHAR(16) NOT NULL DEFAULT 'manual' AFTER `ai_audit_time`;

ALTER TABLE `lost_found`
  ADD COLUMN `ai_risk_level` TINYINT DEFAULT NULL COMMENT '0低风险 1中风险 2高风险' AFTER `audit_reason`,
  ADD COLUMN `ai_audit_reason` VARCHAR(500) DEFAULT NULL AFTER `ai_risk_level`,
  ADD COLUMN `ai_audit_time` DATETIME DEFAULT NULL AFTER `ai_audit_reason`,
  ADD COLUMN `audit_source` VARCHAR(16) NOT NULL DEFAULT 'manual' AFTER `ai_audit_time`;

ALTER TABLE `post`
  ADD COLUMN `ai_risk_level` TINYINT DEFAULT NULL COMMENT '0低风险 1中风险 2高风险' AFTER `audit_reason`,
  ADD COLUMN `ai_audit_reason` VARCHAR(500) DEFAULT NULL AFTER `ai_risk_level`,
  ADD COLUMN `ai_audit_time` DATETIME DEFAULT NULL AFTER `ai_audit_reason`,
  ADD COLUMN `audit_source` VARCHAR(16) NOT NULL DEFAULT 'manual' AFTER `ai_audit_time`;

INSERT INTO `ai_config` (`config_key`, `config_value`, `description`)
VALUES ('audit_enabled', 'false', '是否启用大模型内容审核；关闭时使用本地规则分级')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `prompt_template` (`scene`, `name`, `content`, `enabled`)
VALUES ('content_audit', '校园内容AI审核',
        '你是校园平台内容安全审核员。判断违法、诈骗、广告引流、危险交易、隐私泄露风险。只返回JSON，level只能是LOW、MEDIUM、HIGH；不确定时返回MEDIUM；不得回显完整联系方式。\n\n{question}', 1);
