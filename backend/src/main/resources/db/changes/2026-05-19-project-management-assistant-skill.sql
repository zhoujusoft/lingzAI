DELETE stb
FROM `skill_tool_binding` stb
JOIN `skill_catalog` sc ON sc.`id` = stb.`skill_id`
WHERE sc.`runtime_skill_name` = 'project-management-assistant'
  AND stb.`tool_name` LIKE 'dataset.DS202605191535356JGP.%';

DELETE FROM `tool_catalog`
WHERE `tool_name` LIKE 'dataset.DS202605191535356JGP.%';

INSERT INTO `integration_dataset_publish_binding` (
  `dataset_id`, `publish_status`, `published_tool_codes`, `published_version`,
  `published_at`, `last_compiled_at`, `last_publish_message`, `created_at`, `updated_at`
)
SELECT
  d.`id`,
  'PUBLISHED',
  'dataset.DS202605191535356JGP.search_dataset_summary,dataset.DS202605191535356JGP.get_dataset_schema,dataset.DS202605191535356JGP.execute_dataset_sql',
  1,
  NOW(),
  NOW(),
  '项目管理助手数据集工具已发布',
  NOW(),
  NOW()
FROM `integration_dataset` d
WHERE d.`dataset_code` = 'DS202605191535356JGP'
  AND NOT EXISTS (
    SELECT 1 FROM `integration_dataset_publish_binding` p WHERE p.`dataset_id` = d.`id`
  );

UPDATE `integration_dataset_publish_binding` p
JOIN `integration_dataset` d ON d.`id` = p.`dataset_id`
SET
  p.`publish_status` = 'PUBLISHED',
  p.`published_tool_codes` = 'dataset.DS202605191535356JGP.search_dataset_summary,dataset.DS202605191535356JGP.get_dataset_schema,dataset.DS202605191535356JGP.execute_dataset_sql',
  p.`published_version` = CASE WHEN p.`published_version` IS NULL OR p.`published_version` < 1 THEN 1 ELSE p.`published_version` END,
  p.`published_at` = COALESCE(p.`published_at`, NOW()),
  p.`last_compiled_at` = NOW(),
  p.`last_publish_message` = '项目管理助手数据集工具已发布',
  p.`updated_at` = NOW()
WHERE d.`dataset_code` = 'DS202605191535356JGP';

INSERT INTO `tool_catalog` (
  `tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `enabled_global`,
  `owner_skill_name`, `source`, `sort_order`, `created_at`, `updated_at`
)
SELECT tool_map.`tool_name`, tool_map.`display_name`, tool_map.`description`, 'DATASET_TOOL', 1, 0,
       NULL, 'dataset:DS202605191535356JGP', tool_map.`sort_order`, NOW(), NOW()
FROM (
  SELECT 'dataset.DS202605191535356JGP.search_dataset_summary' AS `tool_name`, '项目管理数据集摘要' AS `display_name`, '查询项目管理数据集的对象摘要，用于定位合同审批、付款审批、云资源采购和项目预警对象。' AS `description`, 71100 AS `sort_order`
  UNION ALL SELECT 'dataset.DS202605191535356JGP.get_dataset_schema', '项目管理数据集结构', '查询项目管理数据集的对象和字段结构，SQL 必须使用返回的对象编码和字段名。', 71101
  UNION ALL SELECT 'dataset.DS202605191535356JGP.execute_dataset_sql', '项目管理数据集查询', '执行项目管理数据集只读 SQL，用于查询重点审批和项目预警明细。', 71102
) tool_map;

INSERT INTO `tool_catalog` (
  `tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `enabled_global`,
  `owner_skill_name`, `source`, `sort_order`, `created_at`, `updated_at`
)
SELECT
  'lowcode.11290.jyzbkb_liuctj',
  '流程提交',
  '调用低代码平台流程提交 API，根据 formcode、dataId、flowid、workItemid、workflowversion、CommentID、shenhyjkey、shenhyjvalue 提交当前待办审批。字段映射：SheetCode -> formcode，objectid -> dataId，RunningInstanceId -> flowid，WorkItemId -> workItemid，WorkflowVersion -> workflowversion，CommentID -> 每次提交临时生成随机GUID，审核意见key -> shenhyjkey。',
  'LOWCODE_API',
  1,
  0,
  NULL,
  'lowcode:11290',
  71103,
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'lowcode.11290.jyzbkb_liuctj');

UPDATE `tool_catalog`
SET
  `display_name` = '流程提交',
  `description` = '调用低代码平台流程提交 API，根据 formcode、dataId、flowid、workItemid、workflowversion、CommentID、shenhyjkey、shenhyjvalue 提交当前待办审批。字段映射：SheetCode -> formcode，objectid -> dataId，RunningInstanceId -> flowid，WorkItemId -> workItemid，WorkflowVersion -> workflowversion，CommentID -> 每次提交临时生成随机GUID，审核意见key -> shenhyjkey。',
  `tool_type` = 'LOWCODE_API',
  `bindable` = 1,
  `enabled_global` = 0,
  `owner_skill_name` = NULL,
  `source` = 'lowcode:11290',
  `sort_order` = 71103,
  `updated_at` = NOW()
WHERE `tool_name` = 'lowcode.11290.jyzbkb_liuctj';

UPDATE `skill_catalog`
SET
  `display_name` = '项目管理助手',
  `description` = '基于项目管理数据集查询云资源采购申请、云采购资源申请、采购申请审批、线索信息、销售合同审批、合同付款、回款预警、成本预警和项目风险，并生成字段表格 HTML 右侧预览。',
  `category` = '项目管理',
  `source` = 'filesystem',
  `version` = '1.0',
  `author` = 'codex-agent',
  `icon` = 'briefcase_business',
  `icon_color` = 'emerald',
  `visible` = 1,
  `sort_order` = 210,
  `updated_at` = NOW()
WHERE `runtime_skill_name` = 'project-management-assistant';

INSERT INTO `skill_catalog` (
  `runtime_skill_name`, `display_name`, `description`, `category`, `source`, `version`,
  `author`, `icon`, `icon_color`, `tool_binding_status`, `tool_binding_message`,
  `visible`, `sort_order`, `created_at`, `updated_at`
)
SELECT
  'project-management-assistant', '项目管理助手',
  '基于项目管理数据集查询云资源采购申请、云采购资源申请、采购申请审批、线索信息、销售合同审批、合同付款、回款预警、成本预警和项目风险，并生成字段表格 HTML 右侧预览。',
  '项目管理', 'filesystem', '1.0', 'codex-agent', 'briefcase_business', 'emerald',
  'NEEDS_REBIND', '请绑定项目管理助手所需的 3 个项目管理数据集工具',
  1, 210, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'project-management-assistant');

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`, `created_at`)
SELECT sc.`id`, tool_map.`tool_name`, 'MANUAL', NOW()
FROM `skill_catalog` sc
JOIN (
  SELECT 'dataset.DS202605191535356JGP.search_dataset_summary' AS `tool_name`
  UNION ALL SELECT 'dataset.DS202605191535356JGP.get_dataset_schema'
  UNION ALL SELECT 'dataset.DS202605191535356JGP.execute_dataset_sql'
  UNION ALL SELECT 'lowcode.11290.jyzbkb_liuctj'
) tool_map
JOIN `tool_catalog` tc ON tc.`tool_name` = tool_map.`tool_name` AND tc.`bindable` = 1
WHERE sc.`runtime_skill_name` = 'project-management-assistant'
  AND NOT EXISTS (
    SELECT 1 FROM `skill_tool_binding` exists_binding
    WHERE exists_binding.`skill_id` = sc.`id` AND exists_binding.`tool_name` = tool_map.`tool_name`
  );

UPDATE `skill_catalog` sc
SET
  `tool_binding_status` = CASE
    WHEN (SELECT COUNT(*) FROM `skill_tool_binding` stb WHERE stb.`skill_id` = sc.`id` AND (stb.`tool_name` LIKE 'dataset.DS202605191535356JGP.%' OR stb.`tool_name` = 'lowcode.11290.jyzbkb_liuctj')) = 4 THEN 'READY'
    ELSE 'NEEDS_REBIND'
  END,
  `tool_binding_message` = CASE
    WHEN (SELECT COUNT(*) FROM `skill_tool_binding` stb WHERE stb.`skill_id` = sc.`id` AND (stb.`tool_name` LIKE 'dataset.DS202605191535356JGP.%' OR stb.`tool_name` = 'lowcode.11290.jyzbkb_liuctj')) = 4 THEN NULL
    ELSE '请绑定项目管理助手所需的 3 个项目管理数据集工具和流程提交工具'
  END,
  `tool_binding_details` = NULL,
  `updated_at` = NOW()
WHERE sc.`runtime_skill_name` = 'project-management-assistant';

INSERT INTO `agent_template_skill_binding` (`template_id`, `skill_id`, `sort_order`, `created_at`)
SELECT at.`id`, sc.`id`, 210, NOW()
FROM `agent_template` at
JOIN `skill_catalog` sc ON sc.`runtime_skill_name` = 'project-management-assistant'
WHERE at.`agent_code` = 'leader-assistant'
  AND NOT EXISTS (
    SELECT 1 FROM `agent_template_skill_binding` atsb
    WHERE atsb.`template_id` = at.`id` AND atsb.`skill_id` = sc.`id`
  );

UPDATE `agent_template_skill_binding` atsb
JOIN `agent_template` at ON at.`id` = atsb.`template_id`
JOIN `skill_catalog` sc ON sc.`id` = atsb.`skill_id`
SET atsb.`sort_order` = 210
WHERE at.`agent_code` = 'leader-assistant'
  AND sc.`runtime_skill_name` = 'project-management-assistant';
