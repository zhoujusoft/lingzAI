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
  'yuqing-daily-report',
  '舆情日报',
  '根据结构化舆情数据生成湖北高校涉稳舆情日报 Word 文档，支持全省高校涉稳舆情与全国高校热点舆情两大板块。',
  '政务办公',
  'filesystem',
  '1.0',
  'codex-agent',
  'feed',
  'red',
  'READY',
  NULL,
  1,
  235,
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1
  FROM `skill_catalog`
  WHERE `runtime_skill_name` = 'yuqing-daily-report'
);

UPDATE `skill_catalog`
SET
  `display_name` = '舆情日报',
  `description` = '根据结构化舆情数据生成湖北高校涉稳舆情日报 Word 文档，支持全省高校涉稳舆情与全国高校热点舆情两大板块。',
  `category` = '政务办公',
  `source` = 'filesystem',
  `version` = '1.0',
  `author` = 'codex-agent',
  `icon` = 'feed',
  `icon_color` = 'red',
  `tool_binding_status` = 'READY',
  `tool_binding_message` = NULL,
  `tool_binding_details` = NULL,
  `visible` = 1,
  `sort_order` = 235,
  `updated_at` = NOW()
WHERE `runtime_skill_name` = 'yuqing-daily-report';

INSERT INTO `agent_template_skill_binding` (`template_id`, `skill_id`, `sort_order`, `created_at`)
SELECT at.`id`, sc.`id`, 235, NOW()
FROM `agent_template` at
JOIN `skill_catalog` sc
  ON sc.`runtime_skill_name` = 'yuqing-daily-report'
WHERE at.`agent_code` = 'general-assistant'
  AND NOT EXISTS (
    SELECT 1
    FROM `agent_template_skill_binding` atsb
    WHERE atsb.`template_id` = at.`id`
      AND atsb.`skill_id` = sc.`id`
  );

UPDATE `agent_template_skill_binding` atsb
JOIN `agent_template` at ON at.`id` = atsb.`template_id`
JOIN `skill_catalog` sc ON sc.`id` = atsb.`skill_id`
SET atsb.`sort_order` = 235
WHERE at.`agent_code` = 'general-assistant'
  AND sc.`runtime_skill_name` = 'yuqing-daily-report';
