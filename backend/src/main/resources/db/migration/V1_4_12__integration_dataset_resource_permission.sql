SET @current_schema = DATABASE();

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @current_schema
              AND TABLE_NAME = 'integration_dataset'
              AND COLUMN_NAME = 'owner_user_id'
        ),
        'SELECT 1',
        'ALTER TABLE `integration_dataset` ADD COLUMN `owner_user_id` bigint DEFAULT NULL COMMENT ''创建人用户ID（历史数据可为空）'' AFTER `description`'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @current_schema
              AND TABLE_NAME = 'integration_dataset'
              AND COLUMN_NAME = 'permission_scope'
        ),
        'SELECT 1',
        'ALTER TABLE `integration_dataset` ADD COLUMN `permission_scope` tinyint DEFAULT NULL COMMENT ''权限范围：1仅创建人可见可操作，2所有人可见仅创建人可操作，3所有人可见可操作（历史数据可为空，由代码兼容）'' AFTER `owner_user_id`'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @current_schema
              AND TABLE_NAME = 'integration_dataset'
              AND INDEX_NAME = 'idx_integration_dataset_owner_user'
        ),
        'SELECT 1',
        'ALTER TABLE `integration_dataset` ADD KEY `idx_integration_dataset_owner_user` (`owner_user_id`)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


SET @current_schema = DATABASE();

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @current_schema
              AND TABLE_NAME = 'mcp_server'
              AND COLUMN_NAME = 'owner_user_id'
        ),
        'SELECT 1',
        'ALTER TABLE `mcp_server` ADD COLUMN `owner_user_id` bigint DEFAULT NULL COMMENT ''创建人用户ID（历史数据可为空）'' AFTER `endpoint`'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @current_schema
              AND TABLE_NAME = 'mcp_server'
              AND COLUMN_NAME = 'permission_scope'
        ),
        'SELECT 1',
        'ALTER TABLE `mcp_server` ADD COLUMN `permission_scope` tinyint DEFAULT NULL COMMENT ''权限范围：1仅创建人可见可操作，2所有人可见仅创建人可操作，3所有人可见可操作（历史数据可为空，由代码兼容）'' AFTER `owner_user_id`'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @current_schema
              AND TABLE_NAME = 'mcp_server'
              AND INDEX_NAME = 'idx_mcp_server_owner_user'
        ),
        'SELECT 1',
        'ALTER TABLE `mcp_server` ADD KEY `idx_mcp_server_owner_user` (`owner_user_id`)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
