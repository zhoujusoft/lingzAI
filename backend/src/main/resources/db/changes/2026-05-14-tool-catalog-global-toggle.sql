-- 2026-05-14-tool-catalog-global-toggle
-- 统一工具库全局可用开关

SET @enabled_global_ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'tool_catalog'
              AND COLUMN_NAME = 'enabled_global'
        ),
        'SELECT ''[db-migrate] skipped add tool_catalog.enabled_global''',
        'ALTER TABLE `tool_catalog` ADD COLUMN `enabled_global` tinyint NOT NULL DEFAULT ''0'' COMMENT ''是否全局可用，1=全局注入对话，0=仅按需绑定/使用'' AFTER `bindable`'
    )
);
PREPARE stmt FROM @enabled_global_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
