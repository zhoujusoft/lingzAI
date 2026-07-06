SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'integration_connector_api'
    AND COLUMN_NAME = 'publish_status'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `integration_connector_api` ADD COLUMN `publish_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''DRAFT'' COMMENT ''发布状态：DRAFT 草稿，PUBLISHED 已发布'' AFTER `enabled`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'integration_connector_api'
    AND COLUMN_NAME = 'published_version'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `integration_connector_api` ADD COLUMN `published_version` int NOT NULL DEFAULT 0 COMMENT ''发布版本'' AFTER `publish_status`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'integration_connector_api'
    AND COLUMN_NAME = 'published_at'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `integration_connector_api` ADD COLUMN `published_at` datetime DEFAULT NULL COMMENT ''最近发布时间'' AFTER `published_version`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `integration_connector_api` api
JOIN `tool_catalog` tc ON tc.`tool_name` = api.`tool_name` AND tc.`tool_type` = 'CONNECTOR_API'
SET api.`publish_status` = 'PUBLISHED',
    api.`published_version` = CASE
        WHEN api.`published_version` IS NULL OR api.`published_version` < 1 THEN 1
        ELSE api.`published_version`
    END,
    api.`published_at` = COALESCE(api.`published_at`, api.`updated_at`)
WHERE api.`tool_name` IS NOT NULL
  AND api.`tool_name` <> '';
