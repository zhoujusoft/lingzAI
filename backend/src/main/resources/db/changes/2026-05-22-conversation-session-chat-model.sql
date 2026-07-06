-- 修复本地 dev 增量链路遗漏的 conversation_session.chat_model_id 字段

SET @chat_model_id_ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'conversation_session'
              AND COLUMN_NAME = 'chat_model_id'
        ),
        'SELECT ''[db-migrate] skipped add conversation_session.chat_model_id''',
        'ALTER TABLE `conversation_session` ADD COLUMN `chat_model_id` bigint DEFAULT NULL COMMENT ''会话绑定的对话模型ID'' AFTER `scope_id`'
    )
);
PREPARE stmt FROM @chat_model_id_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
