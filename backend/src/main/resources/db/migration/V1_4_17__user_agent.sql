SET @current_schema = DATABASE();

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = @current_schema
              AND TABLE_NAME = 'user_agent'
        ),
        'SELECT 1',
        'CREATE TABLE `user_agent` (
            `id` bigint NOT NULL AUTO_INCREMENT COMMENT ''主键'',
            `user_id` bigint NOT NULL COMMENT ''用户ID'',
            `agent_id` bigint NOT NULL COMMENT ''agent_template.id'',
            `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
            `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_user_agent_user` (`user_id`),
            KEY `idx_user_agent_agent_id` (`agent_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT=''用户当前Agent配置表'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
