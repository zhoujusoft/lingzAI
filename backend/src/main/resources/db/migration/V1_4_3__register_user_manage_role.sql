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
