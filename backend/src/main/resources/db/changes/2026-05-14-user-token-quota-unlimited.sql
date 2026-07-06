-- 用户 token 额度账户增加无限制选项

SET @is_unlimited_ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'user_token_account'
              AND COLUMN_NAME = 'is_unlimited'
        ),
        'SELECT ''[db-migrate] skipped add user_token_account.is_unlimited''',
        'ALTER TABLE `user_token_account` ADD COLUMN `is_unlimited` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否无限制'' AFTER `remaining_tokens`'
    )
);
PREPARE stmt FROM @is_unlimited_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
