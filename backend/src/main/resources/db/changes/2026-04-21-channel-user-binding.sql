CREATE TABLE IF NOT EXISTS `channel_user_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '绑定主键',
  `channel_id` bigint NOT NULL COMMENT '关联渠道ID',
  `channel_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道类型',
  `owner_user_id` bigint NOT NULL COMMENT '系统用户ID',
  `route_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '路由类型 GENERAL_CHAT/SKILL_CHAT/DATASET_CHAT',
  `route_target_id` bigint DEFAULT NULL COMMENT '路由目标ID',
  `runtime_context_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '渠道运行时上下文 JSON，例如微信登录上下文',
  `runtime_context_updated_at` datetime DEFAULT NULL COMMENT '运行时上下文更新时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_user_binding` (`channel_id`, `owner_user_id`),
  KEY `idx_channel_user_binding_channel_type` (`channel_type`),
  KEY `idx_channel_user_binding_owner_user` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='渠道用户绑定表';

-- 检查并添加 owner_user_id 列
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_session_binding' AND COLUMN_NAME = 'owner_user_id');
SET @add_col := IF(@col_exists = 0, 'ALTER TABLE `channel_session_binding` ADD COLUMN `owner_user_id` bigint DEFAULT NULL COMMENT ''当前外部会话归属的系统用户ID'' AFTER `reply_target`', 'SELECT 1');
PREPARE stmt FROM @add_col; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 检查索引是否存在
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_session_binding' AND INDEX_NAME = 'idx_channel_session_binding_owner_user');
SET @add_idx := IF(@idx_exists = 0, 'ALTER TABLE `channel_session_binding` ADD KEY `idx_channel_session_binding_owner_user` (`owner_user_id`)', 'SELECT 1');
PREPARE stmt FROM @add_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE `channel_config`
    MODIFY COLUMN `owner_user_id` bigint DEFAULT NULL COMMENT '渠道默认归属用户ID，作为兜底配置';
