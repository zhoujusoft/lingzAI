CREATE TABLE IF NOT EXISTS `conversation_run` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '执行主键',
  `run_code` char(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行编码(ULID)',
  `session_id` bigint NOT NULL COMMENT '关联会话ID',
  `trigger_message_id` bigint NOT NULL COMMENT '触发消息ID',
  `final_message_id` bigint DEFAULT NULL COMMENT '最终assistant消息ID',
  `run_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'CHAT/TOOL/CODE/SKILL/DATASET',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'PENDING/RUNNING/WAITING_INPUT/WAITING_APPROVAL/SUCCEEDED/FAILED/CANCELLED',
  `phase` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'TRIAGE/REASONING/ACTION/OBSERVATION/FINALIZING',
  `sub_stage` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执行子阶段',
  `current_task` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前任务摘要',
  `current_runtime_skill_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前技能',
  `context_json` json DEFAULT NULL COMMENT '轻量运行上下文快照',
  `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误码',
  `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误信息',
  `create_user_id` bigint NOT NULL COMMENT '创建用户ID',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_run_code` (`run_code`),
  KEY `idx_conversation_run_session` (`session_id`,`id`),
  KEY `idx_conversation_run_trigger_message` (`trigger_message_id`,`id`),
  KEY `idx_conversation_run_status` (`status`,`updated_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='会话执行主表';

SET @event_run_id_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'conversation_event'
      AND COLUMN_NAME = 'run_id'
);
SET @event_phase_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'conversation_event'
      AND COLUMN_NAME = 'phase'
);
SET @event_sub_stage_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'conversation_event'
      AND COLUMN_NAME = 'sub_stage'
);
SET @event_tool_name_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'conversation_event'
      AND COLUMN_NAME = 'tool_name'
);
SET @event_status_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'conversation_event'
      AND COLUMN_NAME = 'event_status'
);

SET @ddl = IF(@event_run_id_exists = 0,
    'ALTER TABLE `conversation_event` ADD COLUMN `run_id` bigint DEFAULT NULL COMMENT ''关联执行ID'' AFTER `message_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@event_phase_exists = 0,
    'ALTER TABLE `conversation_event` ADD COLUMN `phase` varchar(32) DEFAULT NULL COMMENT ''执行阶段'' AFTER `event_subtype`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@event_sub_stage_exists = 0,
    'ALTER TABLE `conversation_event` ADD COLUMN `sub_stage` varchar(64) DEFAULT NULL COMMENT ''执行子阶段'' AFTER `phase`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@event_tool_name_exists = 0,
    'ALTER TABLE `conversation_event` ADD COLUMN `tool_name` varchar(128) DEFAULT NULL COMMENT ''工具名'' AFTER `sub_stage`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@event_status_exists = 0,
    'ALTER TABLE `conversation_event` ADD COLUMN `event_status` varchar(32) DEFAULT NULL COMMENT ''事件状态'' AFTER `tool_name`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @event_run_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'conversation_event'
      AND INDEX_NAME = 'idx_conversation_event_run_seq'
);
SET @ddl = IF(@event_run_index_exists = 0,
    'ALTER TABLE `conversation_event` ADD KEY `idx_conversation_event_run_seq` (`run_id`,`sequence_no`,`id`)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @asset_run_id_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'runtime_file_asset'
      AND COLUMN_NAME = 'run_id'
);
SET @ddl = IF(@asset_run_id_exists = 0,
    'ALTER TABLE `runtime_file_asset` ADD COLUMN `run_id` bigint DEFAULT NULL COMMENT ''关联执行ID'' AFTER `session_code`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @asset_run_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'runtime_file_asset'
      AND INDEX_NAME = 'idx_runtime_file_asset_run'
);
SET @ddl = IF(@asset_run_index_exists = 0,
    'ALTER TABLE `runtime_file_asset` ADD KEY `idx_runtime_file_asset_run` (`run_id`,`id`)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
