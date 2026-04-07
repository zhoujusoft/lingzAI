INSERT INTO `skill_catalog` (
  `runtime_skill_name`,
  `display_name`,
  `description`,
  `category`,
  `source`,
  `visible`,
  `sort_order`,
  `created_at`,
  `updated_at`
)
SELECT
  'procurement-contract-review',
  '采购合同审核',
  '从甲方视角系统性审核采购合同，识别付款、交付、验收、违约、售后等核心条款风险，并给出可执行修改建议。',
  '法务合规',
  'filesystem',
  1,
  180,
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1
  FROM `skill_catalog`
  WHERE `runtime_skill_name` = 'procurement-contract-review'
);

UPDATE `skill_catalog`
SET
  `display_name` = '采购合同审核',
  `description` = '从甲方视角系统性审核采购合同，识别付款、交付、验收、违约、售后等核心条款风险，并给出可执行修改建议。',
  `category` = '法务合规',
  `source` = 'filesystem',
  `visible` = 1,
  `sort_order` = 180,
  `updated_at` = NOW()
WHERE `runtime_skill_name` = 'procurement-contract-review';

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`, `created_at`)
SELECT sc.`id`, t.`tool_name`, 'NATIVE', NOW()
FROM `skill_catalog` sc
JOIN (
  SELECT 'readFile' AS `tool_name`
  UNION ALL
  SELECT 'writeFile'
  UNION ALL
  SELECT 'runPython'
) t
WHERE sc.`runtime_skill_name` = 'procurement-contract-review'
  AND NOT EXISTS (
    SELECT 1
    FROM `skill_tool_binding` stb
    WHERE stb.`skill_id` = sc.`id`
      AND stb.`tool_name` = t.`tool_name`
  );

UPDATE `skill_tool_binding` stb
JOIN `skill_catalog` sc ON sc.`id` = stb.`skill_id`
SET stb.`binding_type` = 'NATIVE'
WHERE sc.`runtime_skill_name` = 'procurement-contract-review'
  AND stb.`tool_name` IN ('readFile', 'writeFile', 'runPython');
