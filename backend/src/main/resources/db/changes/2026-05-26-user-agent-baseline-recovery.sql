-- 2026-05-26-user-agent-baseline-recovery
-- 目的：兼容历史环境先以 schema.sql baseline=1.4.17 启动，导致 user_agent 表或个人助手配置字段未被补齐。

SET @current_schema = DATABASE();

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = @current_schema
              AND TABLE_NAME = 'user_agent'
        ),
        'SELECT 1',
        'CREATE TABLE `user_agent` (
            `id` bigint NOT NULL AUTO_INCREMENT COMMENT ''主键'',
            `user_id` bigint NOT NULL COMMENT ''用户ID'',
            `agent_id` bigint NOT NULL COMMENT ''agent_template.id'',
            `agent_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''用户自定义助手名称'',
            `avatar_object_name` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''用户自定义助手头像对象路径'',
            `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
            `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_user_agent_user` (`user_id`),
            KEY `idx_user_agent_agent_id` (`agent_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT=''用户当前Agent配置表'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dbname = DATABASE();

SET @tablename = 'user_agent';
SET @columnname = 'agent_name';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''用户自定义助手名称'' AFTER `agent_id`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @tablename = 'user_agent';
SET @columnname = 'avatar_object_name';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''用户自定义助手头像对象路径'' AFTER `agent_name`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;
