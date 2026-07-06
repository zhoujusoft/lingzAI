INSERT INTO `skill_catalog` (
  `runtime_skill_name`,
  `display_name`,
  `description`,
  `category`,
  `source`,
  `version`,
  `author`,
  `icon`,
  `icon_color`,
  `tool_binding_status`,
  `tool_binding_message`,
  `visible`,
  `sort_order`,
  `created_at`,
  `updated_at`
)
SELECT
  'register-user',
  '注册用户助手',
  '帮助管理员收集注册信息、确认摘要并创建系统用户。',
  '系统管理',
  'filesystem',
  '1.0',
  'codex-agent',
  'person_add',
  'cyan',
  'READY',
  NULL,
  0,
  210,
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'register-user'
);

UPDATE `skill_catalog`
SET
  `display_name` = '注册用户助手',
  `description` = '帮助管理员收集注册信息、确认摘要并创建系统用户。',
  `category` = '系统管理',
  `source` = 'filesystem',
  `version` = '1.0',
  `author` = 'codex-agent',
  `icon` = 'person_add',
  `icon_color` = 'cyan',
  `tool_binding_status` = 'READY',
  `tool_binding_message` = NULL,
  `tool_binding_details` = NULL,
  `visible` = 0,
  `sort_order` = 210,
  `updated_at` = NOW()
WHERE `runtime_skill_name` = 'register-user';

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'register_user_preview', '注册用户预览', '预校验并生成注册确认摘要，不真正创建用户。', 'SKILL_NATIVE', 0, 'register-user', 'filesystem', 210
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'register_user_preview');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'register_user_confirm', '注册用户确认创建', '在管理员明确确认后真正创建用户。', 'SKILL_NATIVE', 0, 'register-user', 'filesystem', 211
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'register_user_confirm');

UPDATE `tool_catalog`
SET
  `display_name` = '注册用户预览',
  `description` = '预校验并生成注册确认摘要，不真正创建用户。',
  `tool_type` = 'SKILL_NATIVE',
  `bindable` = 0,
  `owner_skill_name` = 'register-user',
  `source` = 'filesystem',
  `sort_order` = 210
WHERE `tool_name` = 'register_user_preview';

UPDATE `tool_catalog`
SET
  `display_name` = '注册用户确认创建',
  `description` = '在管理员明确确认后真正创建用户。',
  `tool_type` = 'SKILL_NATIVE',
  `bindable` = 0,
  `owner_skill_name` = 'register-user',
  `source` = 'filesystem',
  `sort_order` = 211
WHERE `tool_name` = 'register_user_confirm';

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`, `created_at`)
SELECT sc.`id`, 'register_user_preview', 'NATIVE', NOW()
FROM `skill_catalog` sc
WHERE sc.`runtime_skill_name` = 'register-user'
  AND NOT EXISTS (
    SELECT 1 FROM `skill_tool_binding` stb
    WHERE stb.`skill_id` = sc.`id` AND stb.`tool_name` = 'register_user_preview'
  );

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`, `created_at`)
SELECT sc.`id`, 'register_user_confirm', 'NATIVE', NOW()
FROM `skill_catalog` sc
WHERE sc.`runtime_skill_name` = 'register-user'
  AND NOT EXISTS (
    SELECT 1 FROM `skill_tool_binding` stb
    WHERE stb.`skill_id` = sc.`id` AND stb.`tool_name` = 'register_user_confirm'
  );

UPDATE `skill_tool_binding` stb
JOIN `skill_catalog` sc ON sc.`id` = stb.`skill_id`
SET stb.`binding_type` = 'NATIVE'
WHERE sc.`runtime_skill_name` = 'register-user'
  AND stb.`tool_name` IN ('register_user_preview', 'register_user_confirm');
