SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'integration_connector_api'
    AND COLUMN_NAME = 'connect_id'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `integration_connector_api` ADD COLUMN `connect_id` varchar(128) DEFAULT NULL COMMENT ''绑定的鉴权ID'' AFTER `connector_id`',
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
    AND COLUMN_NAME = 'connect_name'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `integration_connector_api` ADD COLUMN `connect_name` varchar(255) DEFAULT NULL COMMENT ''绑定的鉴权名称'' AFTER `connect_id`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
