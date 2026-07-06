INSERT INTO `tool_catalog` (
  `tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `enabled_global`,
  `owner_skill_name`, `source`, `sort_order`, `created_at`, `updated_at`
)
SELECT
  'dataset.DS202605191535356JGP.execute_dataset_sql',
  '项目管理数据集查询',
  '执行项目管理数据集只读 SQL，用于查询项目审批、预警和超期回款明细。',
  'DATASET_TOOL',
  1,
  0,
  NULL,
  'dataset:DS202605191535356JGP',
  71102,
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'dataset.DS202605191535356JGP.execute_dataset_sql'
);

UPDATE `tool_catalog`
SET
  `display_name` = '项目管理数据集查询',
  `description` = '执行项目管理数据集只读 SQL，用于查询项目审批、预警和超期回款明细。',
  `tool_type` = 'DATASET_TOOL',
  `bindable` = 1,
  `enabled_global` = 0,
  `owner_skill_name` = NULL,
  `source` = 'dataset:DS202605191535356JGP',
  `sort_order` = 71102,
  `updated_at` = NOW()
WHERE `tool_name` = 'dataset.DS202605191535356JGP.execute_dataset_sql';

INSERT INTO `tool_catalog` (
  `tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `enabled_global`,
  `owner_skill_name`, `source`, `sort_order`, `created_at`, `updated_at`
)
SELECT
  'lowcode.11290.jyzbkb_cuiktx',
  '催款提醒',
  '调用低代码平台催款提醒 API，根据客户名称、项目名称、合同金额、计划回款金额、计划回款日期、逾期天数、销售负责人和销售负责人用户id创建催款提醒。',
  'LOWCODE_API',
  1,
  0,
  NULL,
  'lowcode:11290',
  71104,
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'lowcode.11290.jyzbkb_cuiktx');

UPDATE `tool_catalog`
SET
  `display_name` = '催款提醒',
  `description` = '调用低代码平台催款提醒 API，根据客户名称、项目名称、合同金额、计划回款金额、计划回款日期、逾期天数、销售负责人和销售负责人用户id创建催款提醒。',
  `tool_type` = 'LOWCODE_API',
  `bindable` = 1,
  `enabled_global` = 0,
  `owner_skill_name` = NULL,
  `source` = 'lowcode:11290',
  `sort_order` = 71104,
  `updated_at` = NOW()
WHERE `tool_name` = 'lowcode.11290.jyzbkb_cuiktx';

INSERT INTO `skill_catalog` (
  `runtime_skill_name`, `display_name`, `description`, `category`, `source`, `version`,
  `author`, `icon`, `icon_color`, `tool_binding_status`, `tool_binding_message`,
  `visible`, `sort_order`, `created_at`, `updated_at`
)
SELECT
  'sales-assistant', '销售助手',
  '面向销售回款催办场景，查询超期回款记录并调用催款提醒工具创建提醒，同时生成字段表格 HTML 右侧预览。',
  '销售', 'filesystem', '1.0', 'codex-agent', 'payments', 'amber',
  'NEEDS_REBIND', '请绑定销售助手所需的项目管理数据集查询工具和催款提醒工具',
  1, 210, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'sales-assistant');

UPDATE `skill_catalog`
SET
  `display_name` = '销售助手',
  `description` = '面向销售回款催办场景，查询超期回款记录并调用催款提醒工具创建提醒，同时生成字段表格 HTML 右侧预览。',
  `category` = '销售',
  `source` = 'filesystem',
  `version` = '1.0',
  `author` = 'codex-agent',
  `icon` = 'payments',
  `icon_color` = 'amber',
  `visible` = 1,
  `sort_order` = 210,
  `updated_at` = NOW()
WHERE `runtime_skill_name` = 'sales-assistant';

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`, `created_at`)
SELECT sc.`id`, tool_map.`tool_name`, 'MANUAL', NOW()
FROM `skill_catalog` sc
JOIN (
  SELECT 'dataset.DS202605191535356JGP.execute_dataset_sql' AS `tool_name`
  UNION ALL SELECT 'lowcode.11290.jyzbkb_cuiktx'
) tool_map
JOIN `tool_catalog` tc ON tc.`tool_name` = tool_map.`tool_name` AND tc.`bindable` = 1
WHERE sc.`runtime_skill_name` = 'sales-assistant'
  AND NOT EXISTS (
    SELECT 1 FROM `skill_tool_binding` stb
    WHERE stb.`skill_id` = sc.`id` AND stb.`tool_name` = tool_map.`tool_name`
  );

UPDATE `skill_tool_binding` stb
JOIN `skill_catalog` sc ON sc.`id` = stb.`skill_id`
SET stb.`binding_type` = 'MANUAL'
WHERE sc.`runtime_skill_name` = 'sales-assistant'
  AND stb.`tool_name` IN (
    'dataset.DS202605191535356JGP.execute_dataset_sql',
    'lowcode.11290.jyzbkb_cuiktx'
  );

UPDATE `skill_catalog` sc
SET
  `tool_binding_status` = CASE
    WHEN (
      SELECT COUNT(*)
      FROM `skill_tool_binding` stb
      WHERE stb.`skill_id` = sc.`id`
        AND stb.`tool_name` IN (
          'dataset.DS202605191535356JGP.execute_dataset_sql',
          'lowcode.11290.jyzbkb_cuiktx'
        )
    ) = 2 THEN 'READY'
    WHEN (
      SELECT COUNT(*)
      FROM `tool_catalog` tc
      WHERE tc.`tool_name` IN (
        'dataset.DS202605191535356JGP.execute_dataset_sql',
        'lowcode.11290.jyzbkb_cuiktx'
      )
        AND tc.`bindable` = 1
    ) = 2 THEN 'NEEDS_REBIND'
    ELSE 'MISSING_DEPENDENCY'
  END,
  `tool_binding_message` = CASE
    WHEN (
      SELECT COUNT(*)
      FROM `skill_tool_binding` stb
      WHERE stb.`skill_id` = sc.`id`
        AND stb.`tool_name` IN (
          'dataset.DS202605191535356JGP.execute_dataset_sql',
          'lowcode.11290.jyzbkb_cuiktx'
        )
    ) = 2 THEN NULL
    WHEN (
      SELECT COUNT(*)
      FROM `tool_catalog` tc
      WHERE tc.`tool_name` IN (
        'dataset.DS202605191535356JGP.execute_dataset_sql',
        'lowcode.11290.jyzbkb_cuiktx'
      )
        AND tc.`bindable` = 1
    ) = 2 THEN '请重新绑定销售助手所需的项目管理数据集查询工具和催款提醒工具'
    ELSE '请先注册并开放销售助手所需的项目管理数据集查询工具和催款提醒工具'
  END,
  `tool_binding_details` = NULL,
  `updated_at` = NOW()
WHERE sc.`runtime_skill_name` = 'sales-assistant';

INSERT INTO `agent_template_skill_binding` (`template_id`, `skill_id`, `sort_order`, `created_at`)
SELECT at.`id`, sc.`id`, 210, NOW()
FROM `agent_template` at
JOIN `skill_catalog` sc ON sc.`runtime_skill_name` = 'sales-assistant'
WHERE at.`agent_code` = 'sales-assistant'
  AND NOT EXISTS (
    SELECT 1
    FROM `agent_template_skill_binding` atsb
    WHERE atsb.`template_id` = at.`id`
      AND atsb.`skill_id` = sc.`id`
  );

UPDATE `agent_template_skill_binding` atsb
JOIN `agent_template` at ON at.`id` = atsb.`template_id`
JOIN `skill_catalog` sc ON sc.`id` = atsb.`skill_id`
SET atsb.`sort_order` = 210
WHERE at.`agent_code` = 'sales-assistant'
  AND sc.`runtime_skill_name` = 'sales-assistant';

INSERT INTO `agent_template_skill_binding` (`template_id`, `skill_id`, `sort_order`, `created_at`)
SELECT at.`id`, sc.`id`, 205, NOW()
FROM `agent_template` at
JOIN `skill_catalog` sc ON sc.`runtime_skill_name` = 'sales-assistant'
WHERE at.`agent_code` = 'leader-assistant'
  AND NOT EXISTS (
    SELECT 1
    FROM `agent_template_skill_binding` atsb
    WHERE atsb.`template_id` = at.`id`
      AND atsb.`skill_id` = sc.`id`
  );

UPDATE `agent_template_skill_binding` atsb
JOIN `agent_template` at ON at.`id` = atsb.`template_id`
JOIN `skill_catalog` sc ON sc.`id` = atsb.`skill_id`
SET atsb.`sort_order` = 205
WHERE at.`agent_code` = 'leader-assistant'
  AND sc.`runtime_skill_name` = 'sales-assistant';
