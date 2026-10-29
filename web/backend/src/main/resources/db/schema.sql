-- ============================================================
-- AI校园综合服务平台 数据库初始化脚本（MySQL 8，35张表，含你画我猜）
-- 通用约定：主键 id BIGINT AUTO_INCREMENT；时间 DATETIME 默认 CURRENT_TIMESTAMP；
-- 用户发布内容统一 audit_status（0待审核/1通过/2驳回）+ audit_reason
-- 执行方式：mysql -uroot -p < schema.sql
--
-- ⚠️ 错题本 v2 说明：本脚本的 wrong_question 表已是 v2 结构（correct_answer +
-- 复习状态字段）。若数据库是 v1 旧库（已有 wrong_question 且列名为 answer），
-- CREATE TABLE IF NOT EXISTS 不会补列，请先执行同目录 migrate_v2_wrongbook.sql 增量升级。
-- 增量迁移脚本按顺序执行：migrate_v2_wrongbook.sql → migrate_v3_wrongbook_analyze.sql
--   → migrate_v4_wrongbook_generated.sql → migrate_v5_images_to_db.sql（图片 base64 入库）。
-- ============================================================

CREATE DATABASE IF NOT EXISTS ai_campus_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE ai_campus_platform;

SET NAMES utf8mb4;

-- ---------------- 3.1 账号与基础 ----------------

-- 用户表（Web账号密码登录）
CREATE TABLE IF NOT EXISTS `user` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_no`      VARCHAR(20)  DEFAULT NULL COMMENT '学号（注册必填，仅格式校验）',
  `nickname`        VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '昵称',
  `password`        VARCHAR(100) DEFAULT NULL COMMENT 'BCrypt密码',
  `phone`           VARCHAR(11)  DEFAULT NULL COMMENT '手机号',
  `avatar`          MEDIUMTEXT DEFAULT NULL COMMENT '头像（base64 data URI）',
  `gender`          TINYINT      NOT NULL DEFAULT 0 COMMENT '0未知 1男 2女',
  `bio`             VARCHAR(255) DEFAULT NULL COMMENT '个人简介',
  `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1禁用',
  `last_login_time` DATETIME     DEFAULT NULL COMMENT '最近登录时间（今日活跃口径）',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_no` (`student_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 管理员表
CREATE TABLE IF NOT EXISTS `admin` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `username`    VARCHAR(32) NOT NULL COMMENT '登录名',
  `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt密码',
  `nickname`    VARCHAR(32) DEFAULT NULL,
  `avatar`      MEDIUMTEXT DEFAULT NULL,
  `role`        VARCHAR(20) NOT NULL DEFAULT 'audit' COMMENT 'super/audit',
  `status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '0正常 1禁用',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- 初始管理员：admin / admin123（BCrypt），首次登录请改密
INSERT INTO `admin` (`username`, `password`, `nickname`, `role`)
SELECT 'admin', '$2a$10$mhhWC1d1vn0htoHLVFgbquJUvefFeCMJAdFLNJb1j4/lXXfnd.lPe', '超级管理员', 'super'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `admin` WHERE `username` = 'admin');

-- ---------------- 3.2 AI 子系统（6张表） ----------------

-- AI会话
CREATE TABLE IF NOT EXISTS `ai_session` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT      NOT NULL COMMENT '所属用户',
  `scene`       VARCHAR(16) NOT NULL DEFAULT 'chat' COMMENT '场景 chat/pdf/code/outline',
  `title`       VARCHAR(64) NOT NULL DEFAULT '新会话',
  `doc_id`      BIGINT      DEFAULT NULL COMMENT '关联PDF文档（pdf场景）',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_scene` (`user_id`, `scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话表';

-- AI消息
CREATE TABLE IF NOT EXISTS `ai_message` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `session_id`  BIGINT      NOT NULL COMMENT '所属会话',
  `role`        VARCHAR(10) NOT NULL COMMENT 'user/assistant/system',
  `content`     MEDIUMTEXT   NOT NULL COMMENT '消息内容（图片消息为 base64）',
  `tokens`      INT         NOT NULL DEFAULT 0 COMMENT '消耗token数',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI消息表';

-- AI调用日志
CREATE TABLE IF NOT EXISTS `ai_call_log` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`           BIGINT      NOT NULL,
  `scene`             VARCHAR(16) NOT NULL COMMENT 'chat/pdf/code_fix/outline/quiz',
  `model`             VARCHAR(32) DEFAULT NULL,
  `prompt_tokens`     INT NOT NULL DEFAULT 0,
  `completion_tokens` INT NOT NULL DEFAULT 0,
  `cost_ms`           INT NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  `status`            TINYINT NOT NULL DEFAULT 0 COMMENT '0成功 1失败',
  `error_msg`         VARCHAR(255) DEFAULT NULL,
  `create_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用日志表';

-- AI配置键值表（改后即时生效，免重启）
CREATE TABLE IF NOT EXISTS `ai_config` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `config_key`   VARCHAR(64) NOT NULL COMMENT '配置键',
  `config_value` VARCHAR(512) NOT NULL DEFAULT '' COMMENT '配置值',
  `description`  VARCHAR(255) DEFAULT NULL,
  `update_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI配置表';

-- 预置AI配置（api_key 为空时回退 application.yml 的 ai.api-key）
INSERT INTO `ai_config` (`config_key`, `config_value`, `description`) VALUES
('base_url', 'https://api.deepseek.com', '模型服务地址（OpenAI兼容）'),
('api_key', '', 'DeepSeek API Key，为空则用application.yml占位'),
('model_name', 'deepseek-chat', '模型名称'),
('temperature', '0.7', '采样温度'),
('max_tokens', '2048', '最大输出token'),
('timeout_ms', '60000', '调用超时毫秒'),
('retry_times', '2', '失败重试次数'),
('rate_limit_per_day', '50', '每用户每日AI调用上限'),
('audit_enabled', 'false', '是否启用大模型内容审核；关闭时使用本地规则分级')
ON DUPLICATE KEY UPDATE `config_key` = VALUES(`config_key`);

-- 提示词模板
CREATE TABLE IF NOT EXISTS `prompt_template` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `scene`       VARCHAR(16) NOT NULL COMMENT 'chat/code_fix/pdf/outline/quiz',
  `name`        VARCHAR(64) NOT NULL COMMENT '模板名',
  `content`     TEXT        NOT NULL COMMENT '模板内容，含 {question} 等占位符',
  `enabled`     TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_scene` (`scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词模板表';

-- 预置提示词模板
INSERT INTO `prompt_template` (`scene`, `name`, `content`) VALUES
('chat', '默认答疑模板', '你是一名耐心的大学课程辅导老师，请用清晰、分步骤的方式解答学生的问题，必要时给出示例。\n\n学生问题：{question}'),
('code_fix', '代码纠错模板', '你是一名资深程序员，请检查以下{language}代码中的错误，输出Markdown格式：\n1. 【错误定位】指出行号与错误原因\n2. 【修复建议】给出修改方法\n3. 【修复后代码】用代码块给出完整修正代码\n\n待检查代码：\n```{language}\n{code}\n```'),
('pdf', 'PDF问答模板', '你正在基于一份课件文档回答学生问题。以下是文档相关内容片段：\n\n{context}\n\n请严格基于上述文档内容回答问题，文档未涉及的内容请说明"文档中未提及"。\n\n问题：{question}'),
('outline', '复习提纲模板', '请为「{subject}」学科中「{topic}」这一主题生成一份结构化复习提纲，要求：层级化要点（最多三级）、每个要点附一句话说明、结尾给出3个自测问题。输出Markdown格式。'),
('quiz', '智能习题模板', '基于以下这道错题，请生成3道同类型、同难度的新习题，每道题给出标准答案与详细解析。输出Markdown格式。\n\n学科：{subject}\n错题：{question}\n正确答案：{answer}'),
('wrong_analyze', '错题智能整理模板', '你是一名资深学科老师。请分析以下错题，只输出一个 JSON 对象（不要输出任何其他文字或代码块标记），字段如下：\n{\n  "questionType": "题型，如：选择/填空/简答/计算/编程",\n  "subject": "推测的学科，如：Java/高等数学/数据结构",\n  "chapter": "所属章节",\n  "difficulty": "难度：易/中/难",\n  "knowledgePoints": "知识点数组，如：[\\"多线程\\",\\"锁\\"]",\n  "errorReason": "错因：概念不清/公式记错/审题错误/计算错误/粗心大意/不会解题/知识点混淆/其他",\n  "summary": "不超过50字的本题知识点摘要"\n}\n\n学科（已知）：{subject}\n题目：{question_text}\n我的答案：{my_answer}\n正确答案：{correct_answer}\n解析：{analysis}'),
('wrong_explain', '错题讲解模板', '你是一名耐心的学科辅导老师。请针对学生做错的这道题，输出 Markdown 格式的讲解：\n1. 【错误分析】指出学生的答案错在哪里、为什么错\n2. 【知识点讲解】讲解本题涉及的核心知识点，分步骤说明\n3. 【正确思路】给出正确的解题思路与答案\n4. 【易错提醒】一句话总结以后再遇到这类题要注意什么\n\n学科：{subject}\n题目：{question_text}\n我的答案：{my_answer}\n正确答案：{correct_answer}\n解析：{analysis}\n错因：{error_reason}'),
('review_plan', '复习计划模板', '你是一名学习规划师。请根据学生错题本中的待复习题目，生成一份今天的复习计划，输出 Markdown：\n1. 【今日复习清单】按优先级列出待复习题目及理由\n2. 【推荐复习顺序】说明先复习什么、后复习什么\n3. 【复习方法建议】针对不同错因给出对应复习方法\n4. 【自测问题】2-3 个检验掌握程度的问题\n\n学科筛选：{subject}\n待复习题目列表：\n{question_list}'),
('content_audit', '校园内容AI审核', '你是校园平台内容安全审核员。判断违法、诈骗、广告引流、危险交易、隐私泄露风险。只返回JSON，level只能是LOW、MEDIUM、HIGH；不确定时返回MEDIUM；不得回显完整联系方式。\n\n{question}')
ON DUPLICATE KEY UPDATE `id` = `id`;

-- PDF文档
CREATE TABLE IF NOT EXISTS `pdf_document` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT       NOT NULL,
  `file_name`    VARCHAR(255) NOT NULL,
  `file_url`     LONGTEXT     NOT NULL COMMENT 'Base64 Data URI 或历史 MinIO 地址',
  `page_count`   INT          NOT NULL DEFAULT 0,
  `text_content` LONGTEXT     COMMENT 'PDFBox提取全文',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0解析中 1成功 2失败-扫描件',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PDF文档表';

-- ---------------- 3.3 学习辅助 ----------------

-- 错题本（v2：快速收录 + 复习闭环）
CREATE TABLE IF NOT EXISTS `wrong_question` (
  `id`                      BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`                 BIGINT      NOT NULL,
  `subject`                 VARCHAR(32) NOT NULL DEFAULT '' COMMENT '学科（空=待整理）',
  `tag`                     VARCHAR(32) DEFAULT NULL COMMENT '标签',
  `question`                TEXT        NOT NULL COMMENT '题目',
  `correct_answer`          TEXT        COMMENT '正确答案',
  `analysis`                TEXT        COMMENT '解析',
  `my_answer`               TEXT        COMMENT '我的答案',
  `error_reason`            VARCHAR(64) DEFAULT NULL COMMENT '错误原因（概念不清/审题错误等）',
  `question_type`           VARCHAR(16) DEFAULT NULL COMMENT '题型（选择/填空/简答等）',
  `chapter`                 VARCHAR(64) DEFAULT NULL COMMENT '章节',
  `difficulty`              VARCHAR(16) DEFAULT NULL COMMENT '难度（易/中/难）',
  `knowledge_points`        VARCHAR(255) DEFAULT NULL COMMENT '知识点（逗号分隔）',
  `question_image`          MEDIUMTEXT DEFAULT NULL COMMENT '题目图片（base64 data URI）',
  `note`                    TEXT        COMMENT '我的笔记',
  `analyze_status`          TINYINT     NOT NULL DEFAULT 0 COMMENT 'AI整理状态 0未整理 1整理失败 2已整理',
  `status`                  TINYINT     NOT NULL DEFAULT 0 COMMENT '掌握状态 0待复习 1复习中 2基本掌握 3已掌握',
  `mastery_score`           INT         NOT NULL DEFAULT 0 COMMENT '掌握度 0-100',
  `review_count`            INT         NOT NULL DEFAULT 0 COMMENT '复习次数',
  `wrong_count`             INT         NOT NULL DEFAULT 1 COMMENT '错误次数',
  `consecutive_correct_count` INT       NOT NULL DEFAULT 0 COMMENT '连续答对次数',
  `last_review_time`        DATETIME    DEFAULT NULL COMMENT '最近复习时间',
  `next_review_time`        DATETIME    DEFAULT NULL COMMENT '下次复习时间',
  `source`                  VARCHAR(16) NOT NULL DEFAULT 'manual' COMMENT 'manual手动/ai自动',
  `create_time`             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_subject` (`user_id`, `subject`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_user_next_review` (`user_id`, `next_review_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题本';

-- 错题复习记录
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

-- AI 生成练习题记录（第三阶段：先作为练习，不直接污染错题本）
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

-- ---------------- 3.4 校园生活 ----------------

-- 闲置物品
CREATE TABLE IF NOT EXISTS `idle_item` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT       NOT NULL COMMENT '发布者',
  `title`         VARCHAR(64)  NOT NULL,
  `description`   TEXT         COMMENT '物品描述',
  `images`        MEDIUMTEXT DEFAULT NULL COMMENT '图片 JSON 数组（base64 data URI）',
  `expect_item`   VARCHAR(128) DEFAULT NULL COMMENT '期望换物',
  `category`      VARCHAR(32)  DEFAULT NULL COMMENT '分类',
  `audit_status`  TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审核 1通过 2驳回',
  `audit_reason`  VARCHAR(255) DEFAULT NULL,
  `ai_risk_level` TINYINT DEFAULT NULL COMMENT '0低风险 1中风险 2高风险',
  `ai_audit_reason` VARCHAR(500) DEFAULT NULL,
  `ai_audit_time` DATETIME DEFAULT NULL,
  `audit_source` VARCHAR(16) NOT NULL DEFAULT 'manual',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0在架 1已预约 2已完成 3已下架',
  `view_count`    INT          NOT NULL DEFAULT 0,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_status` (`audit_status`, `status`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='闲置物品表';

-- 闲置预约
CREATE TABLE IF NOT EXISTS `idle_appointment` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `item_id`     BIGINT      NOT NULL,
  `buyer_id`    BIGINT      NOT NULL COMMENT '预约方',
  `seller_id`   BIGINT      NOT NULL COMMENT '物品所属方',
  `message`     VARCHAR(255) DEFAULT NULL COMMENT '预约留言',
  `status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '0待确认 1已接受 2已拒绝 3已完成 4已取消',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_item` (`item_id`),
  KEY `idx_buyer` (`buyer_id`),
  KEY `idx_seller` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='闲置预约表';

-- 闲置互评
CREATE TABLE IF NOT EXISTS `idle_review` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `appointment_id` BIGINT      NOT NULL,
  `from_user_id`   BIGINT      NOT NULL COMMENT '评价方',
  `to_user_id`     BIGINT      NOT NULL COMMENT '被评价方',
  `score`          TINYINT     NOT NULL COMMENT '1-5分',
  `content`        VARCHAR(255) DEFAULT NULL,
  `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_appoint_from` (`appointment_id`, `from_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='闲置互评表';

-- 活动
CREATE TABLE IF NOT EXISTS `activity` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT       NOT NULL COMMENT '发布者',
  `title`           VARCHAR(64)  NOT NULL,
  `description`     TEXT,
  `images`          MEDIUMTEXT DEFAULT NULL COMMENT '图片 JSON 数组（base64 data URI）',
  `category`        VARCHAR(32)  DEFAULT NULL,
  `location`        VARCHAR(128) DEFAULT NULL,
  `start_time`      DATETIME     DEFAULT NULL,
  `end_time`        DATETIME     DEFAULT NULL,
  `signup_deadline` DATETIME     DEFAULT NULL COMMENT '报名截止',
  `max_members`     INT          NOT NULL DEFAULT 0 COMMENT '人数上限，0不限',
  `audit_status`    TINYINT      NOT NULL DEFAULT 0,
  `audit_reason`    VARCHAR(255) DEFAULT NULL,
  `ai_risk_level` TINYINT DEFAULT NULL COMMENT '0低风险 1中风险 2高风险',
  `ai_audit_reason` VARCHAR(500) DEFAULT NULL,
  `ai_audit_time` DATETIME DEFAULT NULL,
  `audit_source` VARCHAR(16) NOT NULL DEFAULT 'manual',
  `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '0报名中 1已满 2已结束 3已下架',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_status` (`audit_status`, `status`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动表';

-- 活动报名（审批制）
CREATE TABLE IF NOT EXISTS `activity_member` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `activity_id` BIGINT      NOT NULL,
  `user_id`     BIGINT      NOT NULL,
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '报名说明/组队信息',
  `status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '0待审批 1已通过 2已拒绝',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_user` (`activity_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动报名表';

-- 活动签到
CREATE TABLE IF NOT EXISTS `activity_signin` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `activity_id` BIGINT   NOT NULL,
  `user_id`     BIGINT   NOT NULL,
  `sign_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_user` (`activity_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动签到表';

-- 失物招领
CREATE TABLE IF NOT EXISTS `lost_found` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT       NOT NULL,
  `type`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0失物 1招领',
  `title`        VARCHAR(64)  NOT NULL,
  `description`  TEXT,
  `images`       MEDIUMTEXT DEFAULT NULL,
  `location`     VARCHAR(128) DEFAULT NULL COMMENT '丢失/拾获地点',
  `happen_time`  DATETIME     DEFAULT NULL COMMENT '发生时间',
  `contact`      VARCHAR(64)  DEFAULT NULL COMMENT '联系方式',
  `audit_status` TINYINT      NOT NULL DEFAULT 0,
  `audit_reason` VARCHAR(255) DEFAULT NULL,
  `ai_risk_level` TINYINT DEFAULT NULL COMMENT '0低风险 1中风险 2高风险',
  `ai_audit_reason` VARCHAR(500) DEFAULT NULL,
  `ai_audit_time` DATETIME DEFAULT NULL,
  `audit_source` VARCHAR(16) NOT NULL DEFAULT 'manual',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0进行中 1已完成 2已下架',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_status` (`audit_status`, `type`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='失物招领表';

-- 公告
CREATE TABLE IF NOT EXISTS `notice` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `admin_id`     BIGINT      NOT NULL COMMENT '发布管理员',
  `title`        VARCHAR(64) NOT NULL,
  `content`      TEXT        COMMENT 'Markdown内容',
  `cover`        MEDIUMTEXT DEFAULT NULL COMMENT '封面图（base64 data URI）',
  `status`       TINYINT     NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2已下线',
  `publish_time` DATETIME    DEFAULT NULL,
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告表';

-- 动态
CREATE TABLE IF NOT EXISTS `post` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT       NOT NULL,
  `content`       TEXT         NOT NULL,
  `images`        MEDIUMTEXT DEFAULT NULL,
  `like_count`    INT          NOT NULL DEFAULT 0,
  `comment_count` INT          NOT NULL DEFAULT 0,
  `audit_status`  TINYINT      NOT NULL DEFAULT 0,
  `audit_reason`  VARCHAR(255) DEFAULT NULL,
  `ai_risk_level` TINYINT DEFAULT NULL COMMENT '0低风险 1中风险 2高风险',
  `ai_audit_reason` VARCHAR(500) DEFAULT NULL,
  `ai_audit_time` DATETIME DEFAULT NULL,
  `audit_source` VARCHAR(16) NOT NULL DEFAULT 'manual',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_status` (`audit_status`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态表';

-- 动态评论（DFA机审，命中敏感词即隐藏）
CREATE TABLE IF NOT EXISTS `post_comment` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `post_id`     BIGINT       NOT NULL,
  `user_id`     BIGINT       NOT NULL,
  `content`     VARCHAR(500) NOT NULL,
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1命中敏感词隐藏',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态评论表';

-- 点赞
CREATE TABLE IF NOT EXISTS `post_like` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `post_id`     BIGINT   NOT NULL,
  `user_id`     BIGINT   NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态点赞表';

-- 举报
CREATE TABLE IF NOT EXISTS `report` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `reporter_id`   BIGINT       NOT NULL COMMENT '举报人',
  `target_type`   VARCHAR(16)  NOT NULL COMMENT 'idle/activity/lostfound/post/comment',
  `target_id`     BIGINT       NOT NULL,
  `reason_type`   VARCHAR(32)  NOT NULL COMMENT '举报类型',
  `reason`        VARCHAR(500) DEFAULT NULL,
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待处理 1已处理',
  `handle_result` VARCHAR(255) DEFAULT NULL,
  `handler_id`    BIGINT       DEFAULT NULL COMMENT '处理管理员',
  `handle_time`   DATETIME     DEFAULT NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报表';

-- 消息通知
CREATE TABLE IF NOT EXISTS `message` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL COMMENT '接收人',
  `type`        VARCHAR(32)  NOT NULL DEFAULT 'system' COMMENT 'system/interact/audit/private_message',
  `title`       VARCHAR(64)  NOT NULL,
  `content`     VARCHAR(500) DEFAULT NULL,
  `biz_type`    VARCHAR(16)  DEFAULT NULL COMMENT '业务类型 idle/activity/...',
  `biz_id`      BIGINT       DEFAULT NULL COMMENT '业务ID',
  `is_read`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `private_unread_key` VARCHAR(96) GENERATED ALWAYS AS (
    CASE WHEN `type` = 'private_message' AND `is_read` = 0
      THEN CONCAT(`user_id`, ':', `biz_type`, ':', `biz_id`) ELSE NULL END
  ) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_private_unread_aggregate` (`private_unread_key`),
  KEY `idx_user_read` (`user_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';

-- 上传资源归属：所有上传先登记所有者，聊天/业务消费时按真实记录校验并绑定
CREATE TABLE IF NOT EXISTS `upload_resource` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `owner_user_id` BIGINT NOT NULL,
  `resource_url` LONGTEXT NOT NULL COMMENT 'Base64 Data URI 或历史 MinIO 地址',
  `resource_type` VARCHAR(16) NOT NULL COMMENT 'image/file',
  `content_type` VARCHAR(128) DEFAULT NULL,
  `file_size` BIGINT NOT NULL DEFAULT 0,
  `biz_type` VARCHAR(32) DEFAULT NULL,
  `biz_id` BIGINT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_upload_owner` (`owner_user_id`, `resource_type`),
  KEY `idx_upload_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传资源归属与消费记录';

-- ---------------- 3.7 一对一私信 ----------------
CREATE TABLE IF NOT EXISTS `chat_conversation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user1_id` BIGINT NOT NULL,
  `user2_id` BIGINT NOT NULL,
  `last_message_id` BIGINT DEFAULT NULL,
  `last_message_summary` VARCHAR(255) DEFAULT NULL,
  `last_message_time` DATETIME DEFAULT NULL,
  `context_type` VARCHAR(32) DEFAULT NULL,
  `context_id` BIGINT DEFAULT NULL,
  `context_title` VARCHAR(128) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_user_pair` (`user1_id`, `user2_id`),
  KEY `idx_chat_last_time` (`last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一对一私信会话';

CREATE TABLE IF NOT EXISTS `chat_conversation_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `conversation_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `unread_count` INT NOT NULL DEFAULT 0,
  `last_read_message_id` BIGINT DEFAULT NULL,
  `read_time` DATETIME DEFAULT NULL,
  `muted` TINYINT NOT NULL DEFAULT 0,
  `hidden` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_member` (`conversation_id`, `user_id`),
  KEY `idx_chat_member_unread` (`user_id`, `unread_count`, `hidden`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私信会话成员状态';

CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `conversation_id` BIGINT NOT NULL,
  `sender_id` BIGINT NOT NULL,
  `receiver_id` BIGINT NOT NULL,
  `client_message_id` VARCHAR(64) NOT NULL,
  `message_type` VARCHAR(16) NOT NULL COMMENT 'text/image',
  `content` MEDIUMTEXT NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0已发送 1已读',
  `read_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_idempotent` (`sender_id`, `client_message_id`),
  KEY `idx_chat_history` (`conversation_id`, `id`),
  KEY `idx_chat_receiver_unread` (`receiver_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私信消息';

CREATE TABLE IF NOT EXISTS `user_block` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `blocked_user_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_block` (`user_id`, `blocked_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户拉黑关系';

-- 敏感词库（启动时与 sensitive-words.txt 合并加载进DFA树）
CREATE TABLE IF NOT EXISTS `sensitive_word` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `word`        VARCHAR(32) NOT NULL,
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词库';


-- BEGIN draw guess schema
CREATE TABLE IF NOT EXISTS `draw_game_room` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `room_code` VARCHAR(12) NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `title` VARCHAR(40) NOT NULL DEFAULT '校园画画房',
  `private_room` TINYINT(1) NOT NULL DEFAULT 0,
  `password_hash` VARCHAR(100) DEFAULT NULL,
  `max_players` TINYINT NOT NULL DEFAULT 6,
  `rounds_per_player` TINYINT NOT NULL DEFAULT 1,
  `status` VARCHAR(16) NOT NULL DEFAULT 'WAITING',
  `current_turn` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_draw_game_room_code` (`room_code`),
  KEY `idx_draw_game_room_status_created` (`status`, `private_room`, `created_at`),
  KEY `idx_draw_game_room_owner` (`owner_user_id`),
  CONSTRAINT `fk_draw_game_room_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='你画我猜房间';

CREATE TABLE IF NOT EXISTS `draw_game_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `room_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `seat_no` TINYINT NOT NULL,
  `nickname` VARCHAR(40) NOT NULL,
  `avatar` MEDIUMTEXT DEFAULT NULL,
  `score` INT NOT NULL DEFAULT 0,
  `active` TINYINT(1) NOT NULL DEFAULT 1,
  `joined_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_visited_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_draw_game_member_room_user` (`room_id`, `user_id`),
  KEY `idx_draw_game_member_room_seat` (`room_id`, `seat_no`),
  KEY `idx_draw_game_member_recent` (`user_id`, `last_visited_at`),
  KEY `idx_draw_game_member_active` (`room_id`, `active`),
  CONSTRAINT `fk_draw_game_member_room` FOREIGN KEY (`room_id`) REFERENCES `draw_game_room` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_draw_game_member_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='你画我猜房间成员';

CREATE TABLE IF NOT EXISTS `draw_game_round` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `room_id` BIGINT NOT NULL,
  `turn_number` INT NOT NULL,
  `drawer_user_id` BIGINT NOT NULL,
  `word` VARCHAR(40) NOT NULL,
  `snapshot_resource_id` BIGINT DEFAULT NULL,
  `drawing_data` MEDIUMTEXT DEFAULT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PLAYING',
  `started_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `ended_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_draw_game_round_turn` (`room_id`, `turn_number`),
  KEY `idx_draw_game_round_gallery` (`status`, `ended_at`),
  KEY `idx_draw_game_round_drawer` (`drawer_user_id`, `started_at`),
  KEY `idx_draw_game_round_snapshot` (`snapshot_resource_id`),
  CONSTRAINT `fk_draw_game_round_room` FOREIGN KEY (`room_id`) REFERENCES `draw_game_room` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_draw_game_round_drawer` FOREIGN KEY (`drawer_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_draw_game_round_snapshot` FOREIGN KEY (`snapshot_resource_id`) REFERENCES `upload_resource` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='你画我猜回合与作品';

CREATE TABLE IF NOT EXISTS `draw_game_word` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `word` VARCHAR(40) NOT NULL,
  `category` VARCHAR(24) NOT NULL DEFAULT '校园生活',
  `active` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_draw_game_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='你画我猜词库';

INSERT INTO `draw_game_word` (`word`, `category`, `active`) VALUES
('梧桐树','校园生活',1),('图书馆','校园生活',1),('宿舍楼','校园生活',1),('食堂','校园生活',1),
('校园卡','校园生活',1),('操场','校园生活',1),('课桌','校园生活',1),('黑板','校园生活',1),
('书包','学习用品',1),('铅笔','学习用品',1),('橡皮','学习用品',1),('笔记本','学习用品',1),
('显微镜','学习用品',1),('地球仪','学习用品',1),('毕业帽','学习用品',1),('眼镜','生活用品',1),
('雨伞','生活用品',1),('闹钟','生活用品',1),('自行车','生活用品',1),('耳机','生活用品',1),
('咖啡','食物饮品',1),('包子','食物饮品',1),('奶茶','食物饮品',1),('西瓜','食物饮品',1),
('饺子','食物饮品',1),('蛋糕','食物饮品',1),('风筝','运动休闲',1),('篮球','运动休闲',1),
('足球','运动休闲',1),('羽毛球','运动休闲',1),('滑板','运动休闲',1),('游泳圈','运动休闲',1),
('小猫','动物植物',1),('小狗','动物植物',1),('兔子','动物植物',1),('金鱼','动物植物',1),
('企鹅','动物植物',1),('长颈鹿','动物植物',1),('向日葵','动物植物',1),('樱花','动物植物',1),
('机器人','想象世界',1),('火箭','想象世界',1),('宇航员','想象世界',1),('魔法棒','想象世界',1),
('彩虹','自然现象',1),('月亮','自然现象',1),('雪人','自然现象',1),('龙卷风','自然现象',1)
ON DUPLICATE KEY UPDATE `category` = VALUES(`category`), `active` = VALUES(`active`);
-- END draw guess schema
