-- 检查并添加 tool_binding_status 列
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill_catalog' AND COLUMN_NAME = 'tool_binding_status');
SET @add_col := IF(@col_exists = 0, 'ALTER TABLE `skill_catalog` ADD COLUMN `tool_binding_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''READY'' COMMENT ''工具绑定状态：READY/MISSING_DEPENDENCY/NEEDS_REBIND/UNSUPPORTED'' AFTER `icon_color`', 'SELECT 1');
PREPARE stmt FROM @add_col; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 检查并添加 tool_binding_message 列
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill_catalog' AND COLUMN_NAME = 'tool_binding_message');
SET @add_col := IF(@col_exists = 0, 'ALTER TABLE `skill_catalog` ADD COLUMN `tool_binding_message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''工具绑定状态说明'' AFTER `tool_binding_status`', 'SELECT 1');
PREPARE stmt FROM @add_col; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 检查并添加 tool_binding_details 列
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill_catalog' AND COLUMN_NAME = 'tool_binding_details');
SET @add_col := IF(@col_exists = 0, 'ALTER TABLE `skill_catalog` ADD COLUMN `tool_binding_details` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT ''工具绑定异常明细(JSON)'' AFTER `tool_binding_message`', 'SELECT 1');
PREPARE stmt FROM @add_col; EXECUTE stmt; DEALLOCATE PREPARE stmt;
