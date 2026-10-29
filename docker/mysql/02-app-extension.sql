USE ai_campus_platform;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `target_type` varchar(16) NOT NULL,
  `target_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`,`target_type`,`target_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `study_partner` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `subject` varchar(64) NOT NULL,
  `goal` varchar(128) DEFAULT '',
  `schedule` varchar(255) DEFAULT '',
  `intro` varchar(500) DEFAULT '',
  `contact` varchar(64) DEFAULT '',
  `status` tinyint DEFAULT '0',
  `audit_status` tinyint DEFAULT '0',
  `audit_reason` varchar(255) DEFAULT NULL,
  `ai_risk_level` tinyint DEFAULT NULL,
  `ai_audit_reason` varchar(255) DEFAULT NULL,
  `ai_audit_time` datetime DEFAULT NULL,
  `audit_source` varchar(20) DEFAULT 'manual',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_partner_audit` (`audit_status`,`status`),
  KEY `idx_partner_subject` (`subject`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习搭子';

CREATE TABLE IF NOT EXISTS `campus_question` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title` varchar(128) NOT NULL,
  `content` text,
  `category` varchar(32) DEFAULT '其他',
  `status` tinyint DEFAULT '0',
  `accepted_answer_id` bigint DEFAULT NULL,
  `view_count` int DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_question_status` (`status`),
  KEY `idx_question_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园互助问答-问题';

CREATE TABLE IF NOT EXISTS `campus_answer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `content` text NOT NULL,
  `is_accepted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_answer_qid` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园互助问答-回答';

CREATE TABLE IF NOT EXISTS `lost_found_claim` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lost_found_id` bigint NOT NULL,
  `claim_user_id` bigint NOT NULL,
  `message` varchar(255) DEFAULT NULL,
  `contact` varchar(64) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_lf` (`lost_found_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


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
