-- 2026-05-12-mcp-server-global
-- 为 MCP 服务添加全局可用开关

SET @dbname = DATABASE();
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
