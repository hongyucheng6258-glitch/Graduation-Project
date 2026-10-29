-- ============================================================
-- 错题本 v2 增量迁移（针对已存在的旧版数据库执行）
-- 旧表 wrong_question 只有 id/user_id/subject/tag/question/answer/
--   analysis/source/create_time 8 列，无复习状态相关字段。
-- 新库直接跑 schema.sql 即可，无需本脚本。
-- ============================================================

-- 1. answer 改名 correct_answer（旧列保留为兼容回滚，但业务不再使用）
ALTER TABLE `wrong_question` CHANGE COLUMN `answer` `correct_answer` TEXT COMMENT '正确答案';

-- 2. 新增复习闭环字段
ALTER TABLE `wrong_question`
  ADD COLUMN `my_answer` TEXT COMMENT '我的答案' AFTER `correct_answer`,
  ADD COLUMN `error_reason` VARCHAR(64) DEFAULT NULL COMMENT '错误原因（概念不清/审题错误等）' AFTER `my_answer`,
  ADD COLUMN `question_type` VARCHAR(16) DEFAULT NULL COMMENT '题型（选择/填空/简答等）' AFTER `error_reason`,
  ADD COLUMN `chapter` VARCHAR(64) DEFAULT NULL COMMENT '章节' AFTER `question_type`,
  ADD COLUMN `difficulty` VARCHAR(16) DEFAULT NULL COMMENT '难度（易/中/难）' AFTER `chapter`,
  ADD COLUMN `knowledge_points` VARCHAR(255) DEFAULT NULL COMMENT '知识点（逗号分隔）' AFTER `difficulty`,
  ADD COLUMN `question_image` VARCHAR(500) DEFAULT NULL COMMENT '题目图片URL' AFTER `knowledge_points`,
  ADD COLUMN `note` TEXT COMMENT '我的笔记' AFTER `question_image`,
  ADD COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '掌握状态 0待复习 1复习中 2基本掌握 3已掌握' AFTER `note`,
  ADD COLUMN `mastery_score` INT NOT NULL DEFAULT 0 COMMENT '掌握度 0-100' AFTER `status`,
  ADD COLUMN `review_count` INT NOT NULL DEFAULT 0 COMMENT '复习次数' AFTER `mastery_score`,
  ADD COLUMN `wrong_count` INT NOT NULL DEFAULT 1 COMMENT '错误次数' AFTER `review_count`,
  ADD COLUMN `consecutive_correct_count` INT NOT NULL DEFAULT 0 COMMENT '连续答对次数' AFTER `wrong_count`,
  ADD COLUMN `last_review_time` DATETIME DEFAULT NULL COMMENT '最近复习时间' AFTER `consecutive_correct_count`,
  ADD COLUMN `next_review_time` DATETIME DEFAULT NULL COMMENT '下次复习时间' AFTER `last_review_time`,
  ADD INDEX `idx_user_status` (`user_id`, `status`),
  ADD INDEX `idx_user_next_review` (`user_id`, `next_review_time`);

-- 3. 新增复习记录表
CREATE TABLE IF NOT EXISTS `wrong_question_review` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`           BIGINT      NOT NULL,
  `wrong_question_id` BIGINT      NOT NULL,
  `user_answer`       TEXT        COMMENT '本次作答',
  `is_correct`        TINYINT     NOT NULL DEFAULT 0 COMMENT '0未答对 1答对',
  `mastery_level`     TINYINT     NOT NULL DEFAULT 0 COMMENT '0仍然不会 1有点理解 2基本掌握 3已完全掌握',
  `review_note`       VARCHAR(500) DEFAULT NULL COMMENT '复习备注',
  `review_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_qid` (`user_id`, `wrong_question_id`),
  KEY `idx_review_time` (`user_id`, `review_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题复习记录';
