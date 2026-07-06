-- 检查并添加 version 列
SET @version_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill_catalog' AND COLUMN_NAME = 'version');
SET @add_version := IF(@version_exists = 0, 'ALTER TABLE `skill_catalog` ADD COLUMN `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''1.0'' COMMENT ''技能版本'' AFTER `source`', 'SELECT 1');
PREPARE stmt FROM @add_version; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 检查并添加 author 列
SET @author_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill_catalog' AND COLUMN_NAME = 'author');
SET @add_author := IF(@author_exists = 0, 'ALTER TABLE `skill_catalog` ADD COLUMN `author` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''zhouju'' COMMENT ''技能作者'' AFTER `version`', 'SELECT 1');
PREPARE stmt FROM @add_author; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 检查并添加 icon 列
SET @icon_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill_catalog' AND COLUMN_NAME = 'icon');
SET @add_icon := IF(@icon_exists = 0, 'ALTER TABLE `skill_catalog` ADD COLUMN `icon` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''grid_view'' COMMENT ''技能主图标'' AFTER `author`', 'SELECT 1');
PREPARE stmt FROM @add_icon; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 检查并添加 icon_color 列
SET @icon_color_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill_catalog' AND COLUMN_NAME = 'icon_color');
SET @add_icon_color := IF(@icon_color_exists = 0, 'ALTER TABLE `skill_catalog` ADD COLUMN `icon_color` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''blue'' COMMENT ''技能图标颜色'' AFTER `icon`', 'SELECT 1');
PREPARE stmt FROM @add_icon_color; EXECUTE stmt; DEALLOCATE PREPARE stmt;
