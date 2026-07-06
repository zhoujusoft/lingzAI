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
  'smm-copper-price-analyst',
  'SMM 铜价分析助手',
  '基于 SMM 历史价格 API 查询指定铜产品区间价格，并输出趋势总结、统计分析、异常识别与双产品对比结论。',
  '金属行情',
  'filesystem',
  '1.0',
  'xiehb',
  'analytics',
  'amber',
  'NEEDS_REBIND',
  '请绑定低代码 API 工具 lowcode.11682.xiehbtest_huoqjg',
  1,
  192,
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1
  FROM `skill_catalog`
  WHERE `runtime_skill_name` = 'smm-copper-price-analyst'
);

UPDATE `skill_catalog`
SET
  `display_name` = 'SMM 铜价分析助手',
  `description` = '基于 SMM 历史价格 API 查询指定铜产品区间价格，并输出趋势总结、统计分析、异常识别与双产品对比结论。',
  `category` = '金属行情',
  `source` = 'filesystem',
  `version` = '1.0',
  `author` = 'xiehb',
  `icon` = 'analytics',
  `icon_color` = 'amber',
  `visible` = 1,
  `sort_order` = 192,
  `updated_at` = NOW()
WHERE `runtime_skill_name` = 'smm-copper-price-analyst';

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`, `created_at`)
SELECT sc.`id`, 'lowcode.11682.xiehbtest_huoqjg', 'MANUAL', NOW()
FROM `skill_catalog` sc
JOIN `tool_catalog` tc
  ON tc.`tool_name` = 'lowcode.11682.xiehbtest_huoqjg'
 AND tc.`bindable` = 1
WHERE sc.`runtime_skill_name` = 'smm-copper-price-analyst'
  AND NOT EXISTS (
    SELECT 1
    FROM `skill_tool_binding` stb
    WHERE stb.`skill_id` = sc.`id`
      AND stb.`tool_name` = 'lowcode.11682.xiehbtest_huoqjg'
  );

UPDATE `skill_tool_binding` stb
JOIN `skill_catalog` sc ON sc.`id` = stb.`skill_id`
SET stb.`binding_type` = 'MANUAL'
WHERE sc.`runtime_skill_name` = 'smm-copper-price-analyst'
  AND stb.`tool_name` = 'lowcode.11682.xiehbtest_huoqjg';

UPDATE `skill_catalog` sc
SET
  `tool_binding_status` = CASE
    WHEN EXISTS (
      SELECT 1
      FROM `skill_tool_binding` stb
      WHERE stb.`skill_id` = sc.`id`
        AND stb.`tool_name` = 'lowcode.11682.xiehbtest_huoqjg'
    ) THEN 'READY'
    WHEN EXISTS (
      SELECT 1
      FROM `tool_catalog` tc
      WHERE tc.`tool_name` = 'lowcode.11682.xiehbtest_huoqjg'
        AND tc.`bindable` = 1
    ) THEN 'NEEDS_REBIND'
    ELSE 'MISSING_DEPENDENCY'
  END,
  `tool_binding_message` = CASE
    WHEN EXISTS (
      SELECT 1
      FROM `skill_tool_binding` stb
      WHERE stb.`skill_id` = sc.`id`
        AND stb.`tool_name` = 'lowcode.11682.xiehbtest_huoqjg'
    ) THEN NULL
    WHEN EXISTS (
      SELECT 1
      FROM `tool_catalog` tc
      WHERE tc.`tool_name` = 'lowcode.11682.xiehbtest_huoqjg'
        AND tc.`bindable` = 1
    ) THEN '请绑定低代码 API 工具 lowcode.11682.xiehbtest_huoqjg'
    ELSE '请先注册低代码 API 工具 lowcode.11682.xiehbtest_huoqjg'
  END,
  `tool_binding_details` = NULL,
  `updated_at` = NOW()
WHERE sc.`runtime_skill_name` = 'smm-copper-price-analyst';
