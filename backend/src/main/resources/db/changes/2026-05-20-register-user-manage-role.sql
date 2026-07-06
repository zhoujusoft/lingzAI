-- 2026-05-20-register-user-manage-role
INSERT IGNORE INTO `sys_role` (`role_code`, `role_name`, `description`, `agent_id`, `enabled`)
VALUES ('manage-user', '管理端用户', '注册用户技能默认绑定角色', NULL, 1);

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.knowledge.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.skillstudio.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.channel-management.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.integration.data-sources.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.skill-management.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.mcp-management.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.model-library.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.token-usage.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.integration.datasets.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.api-library.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';

INSERT IGNORE INTO `sys_role_menu_permission` (`role_id`, `menu_key`)
SELECT r.`id`, 'admin.tool-library.view'
FROM `sys_role` r
WHERE r.`role_code` = 'manage-user';
