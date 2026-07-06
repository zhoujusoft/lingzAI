-- 2026-05-26-user-avatar
SET @current_schema = DATABASE();

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @current_schema
              AND TABLE_NAME = 't_user'
              AND COLUMN_NAME = 'avatar_object_name'
        ),
        'SELECT 1',
        'ALTER TABLE `t_user` ADD COLUMN `avatar_object_name` varchar(512) DEFAULT NULL COMMENT ''头像对象路径'' AFTER `role_id`'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
