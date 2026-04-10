SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'integration_dataset_object_binding'
              AND COLUMN_NAME = 'form_code'
        ),
        'SELECT 1',
        'ALTER TABLE `integration_dataset_object_binding` ADD COLUMN `form_code` varchar(128) DEFAULT NULL COMMENT ''低代码菜单编码'' AFTER `object_code`'
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
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'integration_dataset_field_binding'
              AND COLUMN_NAME = 'form_code'
        ),
        'SELECT 1',
        'ALTER TABLE `integration_dataset_field_binding` ADD COLUMN `form_code` varchar(128) DEFAULT NULL COMMENT ''低代码菜单编码'' AFTER `object_code`'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
