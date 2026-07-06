SET @current_schema = DATABASE();

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @current_schema
              AND TABLE_NAME = 'skill_catalog'
              AND COLUMN_NAME = 'owner_user_id'
        ),
        'SELECT 1',
        'ALTER TABLE `skill_catalog` ADD COLUMN `owner_user_id` bigint DEFAULT NULL COMMENT ''创建人用户ID（历史数据可为空）'' AFTER `source`'
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
              AND TABLE_NAME = 'skill_catalog'
              AND INDEX_NAME = 'idx_skill_catalog_owner_user'
        ),
        'SELECT 1',
        'ALTER TABLE `skill_catalog` ADD KEY `idx_skill_catalog_owner_user` (`owner_user_id`)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
