SET @segments_json_ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'conversation_message'
              AND COLUMN_NAME = 'segments_json'
        ),
        'SELECT ''[db-migrate] skipped add conversation_message.segments_json''',
        'ALTER TABLE `conversation_message` ADD COLUMN `segments_json` json DEFAULT NULL COMMENT ''消息时间线片段JSON'' AFTER `content`'
    )
);
PREPARE stmt FROM @segments_json_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
