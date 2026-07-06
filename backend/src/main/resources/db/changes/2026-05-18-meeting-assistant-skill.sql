DELETE stb
FROM `skill_tool_binding` stb
JOIN `skill_catalog` sc ON sc.`id` = stb.`skill_id`
WHERE sc.`runtime_skill_name` = 'meeting-assistant'
  AND (stb.`tool_name` LIKE 'dataset.meeting_assistant.%'
       OR stb.`tool_name` LIKE 'dataset.DS20260518172145VHZB.%');

DELETE FROM `tool_catalog`
WHERE (`tool_name` LIKE 'dataset.meeting_assistant.%' AND `source` = 'dataset:meeting_assistant')
   OR `tool_name` LIKE 'dataset.DS20260518172145VHZB.%';

INSERT INTO `integration_dataset_publish_binding` (
  `dataset_id`, `publish_status`, `published_tool_codes`, `published_version`,
  `published_at`, `last_compiled_at`, `last_publish_message`, `created_at`, `updated_at`
)
SELECT
  d.`id`,
  'PUBLISHED',
  'dataset.DS20260518172145VHZB.search_dataset_summary,dataset.DS20260518172145VHZB.get_dataset_schema,dataset.DS20260518172145VHZB.execute_dataset_sql',
  1,
  NOW(),
  NOW(),
  '会议助手数据集工具已发布',
  NOW(),
  NOW()
FROM `integration_dataset` d
WHERE d.`dataset_code` = 'DS20260518172145VHZB'
  AND NOT EXISTS (
    SELECT 1 FROM `integration_dataset_publish_binding` p WHERE p.`dataset_id` = d.`id`
  );

UPDATE `integration_dataset_publish_binding` p
JOIN `integration_dataset` d ON d.`id` = p.`dataset_id`
SET
  p.`publish_status` = 'PUBLISHED',
  p.`published_tool_codes` = 'dataset.DS20260518172145VHZB.search_dataset_summary,dataset.DS20260518172145VHZB.get_dataset_schema,dataset.DS20260518172145VHZB.execute_dataset_sql',
  p.`published_version` = CASE WHEN p.`published_version` IS NULL OR p.`published_version` < 1 THEN 1 ELSE p.`published_version` END,
  p.`published_at` = COALESCE(p.`published_at`, NOW()),
  p.`last_compiled_at` = NOW(),
  p.`last_publish_message` = '会议助手数据集工具已发布',
  p.`updated_at` = NOW()
WHERE d.`dataset_code` = 'DS20260518172145VHZB';

INSERT INTO `tool_catalog` (
  `tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `enabled_global`,
  `owner_skill_name`, `source`, `sort_order`, `created_at`, `updated_at`
)
SELECT tool_map.`tool_name`, tool_map.`display_name`, tool_map.`description`, 'DATASET_TOOL', 1, 0,
       NULL, 'dataset:DS20260518172145VHZB', tool_map.`sort_order`, NOW(), NOW()
FROM (
  SELECT 'dataset.DS20260518172145VHZB.search_dataset_summary' AS `tool_name`, '重点事项数据集摘要' AS `display_name`, '查询重点事项数据集的对象摘要，用于定位会议室预订对象。' AS `description`, 71000 AS `sort_order`
  UNION ALL SELECT 'dataset.DS20260518172145VHZB.get_dataset_schema', '重点事项数据集结构', '查询重点事项数据集的对象和字段结构，SQL 必须使用返回的对象编码和字段名。', 71001
  UNION ALL SELECT 'dataset.DS20260518172145VHZB.execute_dataset_sql', '重点事项数据集查询', '执行重点事项数据集只读 SQL，用于查询会议室名称、会议时间、参会人员、会议标题和会议内容。', 71002
) tool_map;

UPDATE `skill_catalog`
SET
  `display_name` = '会议助手',
  `description` = '基于重点事项数据集查询会议室预订、会议时间、参会人员、会议标题和会议内容，并生成会议详情 HTML 右侧预览。仅处理会议相关问题，不处理云资源采购、采购申请、合同审批或项目预警。',
  `category` = '行政办公',
  `source` = 'filesystem',
  `version` = '1.0',
  `author` = 'codex-agent',
  `icon` = 'meeting_room',
  `icon_color` = 'cyan',
  `visible` = 1,
  `sort_order` = 220,
  `updated_at` = NOW()
WHERE `runtime_skill_name` = 'meeting-assistant';

INSERT INTO `skill_catalog` (
  `runtime_skill_name`, `display_name`, `description`, `category`, `source`, `version`,
  `author`, `icon`, `icon_color`, `tool_binding_status`, `tool_binding_message`,
  `visible`, `sort_order`, `created_at`, `updated_at`
)
SELECT
  'meeting-assistant', '会议助手',
  '基于重点事项数据集查询会议室预订、会议时间、参会人员、会议标题和会议内容，并生成会议详情 HTML 右侧预览。仅处理会议相关问题，不处理云资源采购、采购申请、合同审批或项目预警。',
  '行政办公', 'filesystem', '1.0', 'codex-agent', 'meeting_room', 'cyan',
  'NEEDS_REBIND', '请绑定会议助手所需的 3 个重点事项数据集工具',
  1, 220, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'meeting-assistant');

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`, `created_at`)
SELECT sc.`id`, tool_map.`tool_name`, 'MANUAL', NOW()
FROM `skill_catalog` sc
JOIN (
  SELECT 'dataset.DS20260518172145VHZB.search_dataset_summary' AS `tool_name`
  UNION ALL SELECT 'dataset.DS20260518172145VHZB.get_dataset_schema'
  UNION ALL SELECT 'dataset.DS20260518172145VHZB.execute_dataset_sql'
) tool_map
JOIN `tool_catalog` tc ON tc.`tool_name` = tool_map.`tool_name` AND tc.`bindable` = 1
WHERE sc.`runtime_skill_name` = 'meeting-assistant';

UPDATE `skill_catalog` sc
SET
  `tool_binding_status` = CASE
    WHEN (SELECT COUNT(*) FROM `skill_tool_binding` stb WHERE stb.`skill_id` = sc.`id` AND stb.`tool_name` LIKE 'dataset.DS20260518172145VHZB.%') = 3 THEN 'READY'
    ELSE 'NEEDS_REBIND'
  END,
  `tool_binding_message` = CASE
    WHEN (SELECT COUNT(*) FROM `skill_tool_binding` stb WHERE stb.`skill_id` = sc.`id` AND stb.`tool_name` LIKE 'dataset.DS20260518172145VHZB.%') = 3 THEN NULL
    ELSE '请绑定会议助手所需的 3 个重点事项数据集工具'
  END,
  `tool_binding_details` = NULL,
  `updated_at` = NOW()
WHERE sc.`runtime_skill_name` = 'meeting-assistant';

INSERT INTO `agent_template_skill_binding` (`template_id`, `skill_id`, `sort_order`, `created_at`)
SELECT at.`id`, sc.`id`, 220, NOW()
FROM `agent_template` at
JOIN `skill_catalog` sc ON sc.`runtime_skill_name` = 'meeting-assistant'
WHERE at.`agent_code` = 'leader-assistant'
  AND NOT EXISTS (
    SELECT 1 FROM `agent_template_skill_binding` atsb
    WHERE atsb.`template_id` = at.`id` AND atsb.`skill_id` = sc.`id`
  );

UPDATE `agent_template_skill_binding` atsb
JOIN `agent_template` at ON at.`id` = atsb.`template_id`
JOIN `skill_catalog` sc ON sc.`id` = atsb.`skill_id`
SET atsb.`sort_order` = 220
WHERE at.`agent_code` = 'leader-assistant'
  AND sc.`runtime_skill_name` = 'meeting-assistant';
