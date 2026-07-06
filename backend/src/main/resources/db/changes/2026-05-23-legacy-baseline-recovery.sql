-- Legacy baseline recovery backfill
--
-- 目的：兼容历史上被 baseline 标记到高版本、但真实 schema 未完全追平的库。
-- 该脚本必须保持幂等，允许在已收敛的环境中重复执行为 no-op。

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
-- MCP server: enabled_global / headers_json
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

-- ============================================
-- knowledge_base / tool_catalog resource permission
-- ============================================
SET @tablename = 'knowledge_base';
SET @columnname = 'owner_user_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` bigint DEFAULT NULL COMMENT ''创建人用户ID（历史数据可为空）'' AFTER `description`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @tablename = 'knowledge_base';
SET @columnname = 'permission_scope';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` tinyint DEFAULT NULL COMMENT ''权限范围：1仅创建人可见可操作，2所有人可见仅创建人可操作，3所有人可见可操作（历史数据可为空，由代码兼容）'' AFTER `owner_user_id`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @tablename = 'tool_catalog';
SET @columnname = 'owner_user_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` bigint DEFAULT NULL COMMENT ''创建人用户ID（历史数据可为空）'' AFTER `source`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @tablename = 'tool_catalog';
SET @columnname = 'permission_scope';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` tinyint DEFAULT NULL COMMENT ''权限范围：1仅创建人可见可操作，2所有人可见仅创建人可操作，3所有人可见可操作（历史数据可为空，由代码兼容）'' AFTER `owner_user_id`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ============================================
-- mcp_server resource permission
-- ============================================
SET @tablename = 'mcp_server';
SET @columnname = 'owner_user_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` bigint DEFAULT NULL COMMENT ''创建人用户ID（历史数据可为空）'' AFTER `endpoint`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @tablename = 'mcp_server';
SET @columnname = 'permission_scope';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` tinyint DEFAULT NULL COMMENT ''权限范围：1仅创建人可见可操作，2所有人可见仅创建人可操作，3所有人可见可操作（历史数据可为空，由代码兼容）'' AFTER `owner_user_id`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @tablename = 'mcp_server';
SET @indexname = 'idx_mcp_server_owner_user';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD KEY `', @indexname, '` (`owner_user_id`)')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ============================================
-- integration_dataset resource permission
-- ============================================
SET @tablename = 'integration_dataset';
SET @columnname = 'owner_user_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` bigint DEFAULT NULL COMMENT ''创建人用户ID（历史数据可为空）'' AFTER `description`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @tablename = 'integration_dataset';
SET @columnname = 'permission_scope';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` tinyint DEFAULT NULL COMMENT ''权限范围：1仅创建人可见可操作，2所有人可见仅创建人可操作，3所有人可见可操作（历史数据可为空，由代码兼容）'' AFTER `owner_user_id`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @tablename = 'integration_dataset';
SET @indexname = 'idx_integration_dataset_owner_user';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD KEY `', @indexname, '` (`owner_user_id`)')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ============================================
-- integration_data_source resource permission
-- ============================================
SET @tablename = 'integration_data_source';
SET @columnname = 'owner_user_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` bigint DEFAULT NULL COMMENT ''创建人用户ID（历史数据可为空）'' AFTER `auth_config_json`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @tablename = 'integration_data_source';
SET @columnname = 'permission_scope';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` tinyint DEFAULT NULL COMMENT ''权限范围：1仅创建人可见可操作，2所有人可见仅创建人可操作，3所有人可见可操作（历史数据可为空，由代码兼容）'' AFTER `owner_user_id`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @tablename = 'integration_data_source';
SET @indexname = 'idx_integration_data_source_owner_user';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD KEY `', @indexname, '` (`owner_user_id`)')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ============================================
-- skill_catalog owner_user_id
-- ============================================
SET @tablename = 'skill_catalog';
SET @columnname = 'owner_user_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` bigint DEFAULT NULL COMMENT ''创建人用户ID（历史数据可为空）'' AFTER `source`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @tablename = 'skill_catalog';
SET @indexname = 'idx_skill_catalog_owner_user';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD KEY `', @indexname, '` (`owner_user_id`)')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;
