-- MCP server 新增内部/外部范围字段
-- 手动执行：为外部平台型 MCP（如无状态 streamable-http）提供适配入口

SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'mcp_server'
              AND COLUMN_NAME = 'server_scope'
        ),
        'SELECT ''[db-migrate] skipped add mcp_server.server_scope''',
        'ALTER TABLE `mcp_server` ADD COLUMN `server_scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''INTERNAL'' COMMENT ''服务范围：INTERNAL/EXTERNAL'' AFTER `description`'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
