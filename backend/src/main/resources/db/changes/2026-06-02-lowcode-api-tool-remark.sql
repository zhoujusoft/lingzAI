-- 任务：lowcode-api-tool-remark
-- 说明：为低代码 API 目录增加独立工具备注字段
-- 影响表：lowcode_api_catalog

SET NAMES utf8mb4;

SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'lowcode_api_catalog'
              AND COLUMN_NAME = 'tool_remark'
        ),
        'SELECT ''[db-migrate] skipped add lowcode_api_catalog.tool_remark''',
        'ALTER TABLE `lowcode_api_catalog` ADD COLUMN `tool_remark` text COMMENT ''工具备注'' AFTER `tool_name`'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
