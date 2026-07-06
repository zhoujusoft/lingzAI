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
  'policy-people-eligibility',
  '政策适用对象筛选',
  '上传政策文件后，智能识别政策适用对象范围，结合人员信息自动筛选符合条件的人群名单，为后续精准触达和政策服务提供支持。',
  '政策服务',
  'filesystem',
  '1.0',
  'zhouju',
  'grid_view',
  'blue',
  'NEEDS_REBIND',
  '请先绑定固定人员数据集的摘要、结构和 SQL 工具',
  1,
  190,
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1
  FROM `skill_catalog`
  WHERE `runtime_skill_name` = 'policy-people-eligibility'
);

UPDATE `skill_catalog`
SET
  `display_name` = '政策适用对象筛选',
  `description` = '上传政策文件后，智能识别政策适用对象范围，结合人员信息自动筛选符合条件的人群名单，为后续精准触达和政策服务提供支持。',
  `category` = '政策服务',
  `source` = 'filesystem',
  `version` = '1.0',
  `author` = 'zhouju',
  `icon` = 'grid_view',
  `icon_color` = 'blue',
  `tool_binding_status` = 'NEEDS_REBIND',
  `tool_binding_message` = '请先绑定固定人员数据集的摘要、结构和 SQL 工具',
  `visible` = 1,
  `sort_order` = 190,
  `updated_at` = NOW()
WHERE `runtime_skill_name` = 'policy-people-eligibility';

-- 绑定固定数据集工具时，补充如下三类 MANUAL 绑定：
-- 1. <dataset-tool-prefix>.search_dataset_summary
-- 2. <dataset-tool-prefix>.get_dataset_schema
-- 3. <dataset-tool-prefix>.execute_dataset_sql
--
-- 示例（将下面 tool_name 替换成目标数据集真实工具名后再执行）：
-- INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`, `created_at`)
-- SELECT sc.`id`, t.`tool_name`, 'MANUAL', NOW()
-- FROM `skill_catalog` sc
-- JOIN (
--   SELECT 'dataset.people.search_dataset_summary' AS `tool_name`
--   UNION ALL
--   SELECT 'dataset.people.get_dataset_schema'
--   UNION ALL
--   SELECT 'dataset.people.execute_dataset_sql'
-- ) t
-- WHERE sc.`runtime_skill_name` = 'policy-people-eligibility'
--   AND NOT EXISTS (
--     SELECT 1
--     FROM `skill_tool_binding` stb
--     WHERE stb.`skill_id` = sc.`id`
--       AND stb.`tool_name` = t.`tool_name`
--   );
