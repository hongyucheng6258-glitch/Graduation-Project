-- ============================================================
-- 错题本 v4 增量迁移（第三阶段：AI 生成练习题记录）
-- 新增 wrong_question_generated 表 + practice 提示词模板
-- 已执行过 v2/v3 迁移的库执行本脚本即可
-- ============================================================

CREATE TABLE IF NOT EXISTS `wrong_question_generated` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`           BIGINT      NOT NULL,
  `wrong_question_id` BIGINT      NOT NULL COMMENT '来源错题',
  `question`          TEXT        NOT NULL COMMENT '练习题题目',
  `options`           TEXT        COMMENT '选项 JSON 数组（选择题）',
  `answer`            TEXT        COMMENT '正确答案',
  `analysis`          TEXT        COMMENT '解析',
  `status`            TINYINT     NOT NULL DEFAULT 0 COMMENT '0练习中 1已加入错题本',
  `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_src` (`user_id`, `wrong_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI生成练习题记录';

INSERT INTO `prompt_template` (`scene`, `name`, `content`)
SELECT * FROM (SELECT 'practice' AS scene, '同类练习模板' AS name,
  '你是一名出题老师。请基于原错题生成一道同类型、同难度的新练习题，只输出一个 JSON 对象（不要输出其他文字或代码块标记）：\n{\n  "question": "题目内容",\n  "options": ["A. 选项1", "B. 选项2", "C. 选项3", "D. 选项4"],（选择题必填数组；填空/简答等题型传空数组 []）\n  "answer": "正确答案（选择题为选项字母如 A；其他为答案文本）",\n  "analysis": "详细解析"\n}\n\n学科：{subject}\n原题：{question_text}\n原答案：{correct_answer}\n原解析：{analysis}' AS content) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `prompt_template` WHERE `scene` = 'practice');
