-- 2026-05-12-mcp-server-headers
-- 为 MCP 服务添加自定义请求头支持

SET @dbname = DATABASE();
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
