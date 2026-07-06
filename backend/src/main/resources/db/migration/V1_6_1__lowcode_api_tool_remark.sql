SET @current_schema = DATABASE();

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @current_schema
              AND TABLE_NAME = 'lowcode_api_catalog'
              AND COLUMN_NAME = 'tool_remark'
        ),
        'SELECT 1',
        'ALTER TABLE `lowcode_api_catalog` ADD COLUMN `tool_remark` text COMMENT ''工具备注'' AFTER `tool_name`'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
