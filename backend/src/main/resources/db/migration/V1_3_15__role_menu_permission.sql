CREATE TABLE IF NOT EXISTS `sys_role_menu_permission` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'role menu permission id',
    `role_id` bigint NOT NULL COMMENT 'role id',
    `menu_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'menu permission key',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`,`menu_key`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='role menu permissions';

INSERT IGNORE INTO `sys_role` (`role_code`, `role_name`, `description`, `agent_id`, `enabled`)
VALUES ('normal-user', '普通用户', '默认普通用户角色', NULL, 1);

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.knowledge.view'
FROM `sys_role` r
WHERE r.`role_code` = 'normal-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.skillstudio.view'
FROM `sys_role` r
WHERE r.`role_code` = 'normal-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.channel-management.view'
FROM `sys_role` r
WHERE r.`role_code` = 'normal-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.integration.data-sources.view'
FROM `sys_role` r
WHERE r.`role_code` = 'normal-user';
