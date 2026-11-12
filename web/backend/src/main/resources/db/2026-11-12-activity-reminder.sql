CREATE TABLE IF NOT EXISTS activity_reminder (
  member_id BIGINT NOT NULL COMMENT '活动报名记录 ID',
  start_time DATETIME NOT NULL COMMENT '发送提醒时的活动开始时间',
  PRIMARY KEY (member_id, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动开始前提醒去重记录';
