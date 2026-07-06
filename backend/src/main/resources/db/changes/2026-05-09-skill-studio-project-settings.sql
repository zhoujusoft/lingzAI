SET @project_hints_json_ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'skill_studio_project'
              AND COLUMN_NAME = 'project_hints_json'
        ),
        'SELECT ''[db-migrate] skipped add skill_studio_project.project_hints_json''',
        'ALTER TABLE `skill_studio_project` ADD COLUMN `project_hints_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT ''项目级生成提示 JSON'' AFTER `initial_prompt`'
    )
);
PREPARE stmt FROM @project_hints_json_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @project_constraints_json_ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'skill_studio_project'
              AND COLUMN_NAME = 'project_constraints_json'
        ),
        'SELECT ''[db-migrate] skipped add skill_studio_project.project_constraints_json''',
        'ALTER TABLE `skill_studio_project` ADD COLUMN `project_constraints_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT ''项目级生成约束 JSON'' AFTER `project_hints_json`'
    )
);
PREPARE stmt FROM @project_constraints_json_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tool_bindings_json_ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'skill_studio_project'
              AND COLUMN_NAME = 'tool_bindings_json'
        ),
        'SELECT ''[db-migrate] skipped add skill_studio_project.tool_bindings_json''',
        'ALTER TABLE `skill_studio_project` ADD COLUMN `tool_bindings_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT ''项目工具绑定配置 JSON'' AFTER `project_constraints_json`'
    )
);
PREPARE stmt FROM @tool_bindings_json_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tool_settings_digest_ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'skill_studio_project'
              AND COLUMN_NAME = 'tool_settings_digest'
        ),
        'SELECT ''[db-migrate] skipped add skill_studio_project.tool_settings_digest''',
        'ALTER TABLE `skill_studio_project` ADD COLUMN `tool_settings_digest` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT ''工具设置摘要'' AFTER `tool_bindings_json`'
    )
);
PREPARE stmt FROM @tool_settings_digest_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @last_generated_tool_digest_ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'skill_studio_project'
              AND COLUMN_NAME = 'last_generated_tool_digest'
        ),
        'SELECT ''[db-migrate] skipped add skill_studio_project.last_generated_tool_digest''',
        'ALTER TABLE `skill_studio_project` ADD COLUMN `last_generated_tool_digest` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT ''最近一次成功生成使用的工具设置摘要'' AFTER `tool_settings_digest`'
    )
);
PREPARE stmt FROM @last_generated_tool_digest_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
