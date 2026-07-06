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
  'business-metrics',
  '经营指标',
  '面向经营分析场景，基于经营指标数据集查询整体经营情况、指标完成趋势与人员完成情况明细，并生成可预览 HTML 页面。',
  '销售',
  'filesystem',
  '1.0',
  'zhouju',
  'monitoring',
  'teal',
  'NEEDS_REBIND',
  '请绑定经营指标技能所需的 3 个数据集工具',
  1,
  195,
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1
  FROM `skill_catalog`
  WHERE `runtime_skill_name` = 'business-metrics'
);

UPDATE `skill_catalog`
SET
  `display_name` = '经营指标',
  `description` = '面向经营分析场景，基于经营指标数据集查询整体经营情况、指标完成趋势与人员完成情况明细，并生成可预览 HTML 页面。',
  `category` = '销售',
  `source` = 'filesystem',
  `version` = '1.0',
  `author` = 'zhouju',
  `icon` = 'monitoring',
  `icon_color` = 'teal',
  `visible` = 1,
  `sort_order` = 195,
  `updated_at` = NOW()
WHERE `runtime_skill_name` = 'business-metrics';

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`, `created_at`)
SELECT sc.`id`, tool_map.`tool_name`, 'MANUAL', NOW()
FROM `skill_catalog` sc
JOIN (
  SELECT 'dataset.jingyingzhibiao.search_dataset_summary' AS `tool_name`
  UNION ALL SELECT 'dataset.jingyingzhibiao.get_dataset_schema'
  UNION ALL SELECT 'dataset.jingyingzhibiao.execute_dataset_sql'
) tool_map
JOIN `tool_catalog` tc
  ON tc.`tool_name` = tool_map.`tool_name`
 AND tc.`bindable` = 1
WHERE sc.`runtime_skill_name` = 'business-metrics'
  AND NOT EXISTS (
    SELECT 1
    FROM `skill_tool_binding` stb
    WHERE stb.`skill_id` = sc.`id`
      AND stb.`tool_name` = tool_map.`tool_name`
  );

UPDATE `skill_tool_binding` stb
JOIN `skill_catalog` sc ON sc.`id` = stb.`skill_id`
SET stb.`binding_type` = 'MANUAL'
WHERE sc.`runtime_skill_name` = 'business-metrics'
  AND stb.`tool_name` IN (
    'dataset.jingyingzhibiao.search_dataset_summary',
    'dataset.jingyingzhibiao.get_dataset_schema',
    'dataset.jingyingzhibiao.execute_dataset_sql'
  );

UPDATE `skill_catalog` sc
SET
  `tool_binding_status` = CASE
    WHEN (
      SELECT COUNT(*)
      FROM `skill_tool_binding` stb
      WHERE stb.`skill_id` = sc.`id`
        AND stb.`tool_name` IN (
          'dataset.jingyingzhibiao.search_dataset_summary',
          'dataset.jingyingzhibiao.get_dataset_schema',
          'dataset.jingyingzhibiao.execute_dataset_sql'
        )
    ) = 3 THEN 'READY'
    WHEN (
      SELECT COUNT(*)
      FROM `tool_catalog` tc
      WHERE tc.`tool_name` IN (
        'dataset.jingyingzhibiao.search_dataset_summary',
        'dataset.jingyingzhibiao.get_dataset_schema',
        'dataset.jingyingzhibiao.execute_dataset_sql'
      )
        AND tc.`bindable` = 1
    ) = 3 THEN 'NEEDS_REBIND'
    ELSE 'MISSING_DEPENDENCY'
  END,
  `tool_binding_message` = CASE
    WHEN (
      SELECT COUNT(*)
      FROM `skill_tool_binding` stb
      WHERE stb.`skill_id` = sc.`id`
        AND stb.`tool_name` IN (
          'dataset.jingyingzhibiao.search_dataset_summary',
          'dataset.jingyingzhibiao.get_dataset_schema',
          'dataset.jingyingzhibiao.execute_dataset_sql'
        )
    ) = 3 THEN NULL
    WHEN (
      SELECT COUNT(*)
      FROM `tool_catalog` tc
      WHERE tc.`tool_name` IN (
        'dataset.jingyingzhibiao.search_dataset_summary',
        'dataset.jingyingzhibiao.get_dataset_schema',
        'dataset.jingyingzhibiao.execute_dataset_sql'
      )
        AND tc.`bindable` = 1
    ) = 3 THEN '请重新绑定经营指标技能所需的 3 个数据集工具'
    ELSE '请先发布并开放经营指标技能所需的 3 个数据集工具'
  END,
  `tool_binding_details` = NULL,
  `updated_at` = NOW()
WHERE sc.`runtime_skill_name` = 'business-metrics';

INSERT INTO `agent_template_skill_binding` (`template_id`, `skill_id`, `sort_order`, `created_at`)
SELECT at.`id`, sc.`id`, 222, NOW()
FROM `agent_template` at
JOIN `skill_catalog` sc
  ON sc.`runtime_skill_name` = 'business-metrics'
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
SET atsb.`sort_order` = 222
WHERE at.`agent_code` = 'sales-assistant'
  AND sc.`runtime_skill_name` = 'business-metrics';
