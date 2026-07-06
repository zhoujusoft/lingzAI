-- Legacy baseline convergence backfill
--
-- 目的：兼容历史上使用 schema.sql baseline 初始化、db/changes 脚本升级、
-- 以及中途迁移版本调整后的库，幂等收敛到当前 schema 快照。

SET @dbname = DATABASE();

-- ============================================
-- tool_catalog.enabled_global
-- ============================================
SET @tablename = 'tool_catalog';
SET @columnname = 'enabled_global';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` tinyint NOT NULL DEFAULT ''0'' COMMENT ''是否全局可用，1=全局注入对话，0=仅按需绑定/使用'' AFTER `bindable`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ============================================
-- user_token_account + token_quota_settings
-- ============================================
CREATE TABLE IF NOT EXISTS `user_token_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `granted_tokens` bigint NOT NULL DEFAULT '0' COMMENT '累计发放 token',
  `consumed_tokens` bigint NOT NULL DEFAULT '0' COMMENT '累计消耗 token',
  `remaining_tokens` bigint NOT NULL DEFAULT '0' COMMENT '剩余 token',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_token_account_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户 token 额度账户表';

INSERT INTO `user_token_account` (`user_id`, `granted_tokens`, `consumed_tokens`, `remaining_tokens`)
SELECT `id`, 1000000, 0, 1000000
FROM `t_user`
WHERE NOT EXISTS (
  SELECT 1 FROM `user_token_account` uta WHERE uta.`user_id` = `t_user`.`id`
);

INSERT INTO `system_config` (`config_key`, `config_value`, `status`)
SELECT 'token_quota_settings', '{"initialGrantTokens":1000000}', 0
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `system_config` WHERE `config_key` = 'token_quota_settings'
);

SET @tablename = 'user_token_account';
SET @columnname = 'is_unlimited';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否无限制'' AFTER `remaining_tokens`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ============================================
-- sys_role_menu_permission + 角色权限种子
-- ============================================
CREATE TABLE IF NOT EXISTS `sys_role_menu_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'role menu permission id',
  `role_id` bigint NOT NULL COMMENT 'role id',
  `menu_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'menu permission key',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`,`menu_key`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='role menu permissions';

INSERT IGNORE INTO `sys_role` (`role_code`, `role_name`, `description`, `agent_id`, `enabled`)
VALUES ('normal-user', '普通用户', '默认普通用户角色', NULL, 1);

INSERT IGNORE INTO `sys_role` (`role_code`, `role_name`, `description`, `agent_id`, `enabled`)
VALUES ('manage-user', '管理端用户', '注册用户技能默认绑定角色', NULL, 1);

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.knowledge.view'
FROM `sys_role` r
WHERE r.`role_code` = 'normal-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.skillstudio.view'
FROM `sys_role` r
WHERE r.`role_code` = 'normal-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.channel-management.view'
FROM `sys_role` r
WHERE r.`role_code` = 'normal-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.integration.data-sources.view'
FROM `sys_role` r
WHERE r.`role_code` = 'normal-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.knowledge.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.skillstudio.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.channel-management.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.integration.data-sources.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.skill-management.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.mcp-management.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.model-library.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.token-usage.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.integration.datasets.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.api-library.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.tool-library.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

-- ============================================
-- conversation_session / conversation_message
-- ============================================
SET @tablename = 'conversation_session';
SET @columnname = 'chat_model_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` bigint DEFAULT NULL COMMENT ''会话绑定的对话模型ID'' AFTER `scope_id`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @tablename = 'conversation_message';
SET @columnname = 'segments_json';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` json DEFAULT NULL COMMENT ''消息时间线片段JSON'' AFTER `content`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ============================================
-- MCP server
-- ============================================
SET @tablename = 'mcp_server';
SET @columnname = 'enabled_global';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` tinyint NOT NULL DEFAULT ''0'' COMMENT ''是否全局可用，1=全局，0=非全局'' AFTER `enabled`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @tablename = 'mcp_server';
SET @columnname = 'headers_json';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT ''自定义请求头 JSON'' AFTER `auth_config_json`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ============================================
-- runtime_file_asset
-- ============================================
CREATE TABLE IF NOT EXISTS `runtime_file_asset` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_code` char(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件编码（ULID）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_id` bigint DEFAULT NULL COMMENT '会话ID',
  `session_code` char(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '会话编码',
  `origin_message_id` bigint DEFAULT NULL COMMENT '来源消息ID',
  `origin_event_id` bigint DEFAULT NULL COMMENT '来源事件ID',
  `parent_file_code` char(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '父文件编码',
  `file_role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件角色 UPLOAD/TEMP/ARTIFACT',
  `producer_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '产生方式 USER_UPLOAD/WRITE_ARTIFACT/SYSTEM',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/DELETED/EXPIRED/FAILED',
  `display_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '展示文件名',
  `storage_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '存储文件名',
  `extension` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '扩展名',
  `content_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'MIME类型',
  `size_bytes` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '内容SHA-256',
  `logical_root` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '逻辑根目录 UPLOADS/OUTPUTS/TEMP/PROFILE',
  `logical_path` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '逻辑相对路径',
  `virtual_path` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '虚拟路径',
  `local_path` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '本地真实路径',
  `local_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PRESENT' COMMENT '本地副本状态 PRESENT/MISSING/DELETED/FAILED',
  `bucket` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'MinIO bucket',
  `object_name` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'MinIO object key',
  `minio_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT 'MinIO状态 PENDING/UPLOADED/MISSING/FAILED',
  `metadata_json` json DEFAULT NULL COMMENT '扩展元数据JSON',
  `expired_at` datetime DEFAULT NULL COMMENT '过期时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_runtime_file_asset_code` (`file_code`),
  KEY `idx_runtime_file_asset_user_session` (`user_id`,`session_id`,`id`),
  KEY `idx_runtime_file_asset_origin_message` (`origin_message_id`,`id`),
  KEY `idx_runtime_file_asset_origin_event` (`origin_event_id`,`id`),
  KEY `idx_runtime_file_asset_role_status` (`file_role`,`status`,`id`),
  KEY `idx_runtime_file_asset_parent` (`parent_file_code`),
  KEY `idx_runtime_file_asset_virtual_path` (`user_id`,`session_id`,`virtual_path`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='运行时文件资产表';
