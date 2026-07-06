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
  'doc-ocr-pipeline',
  '文档OCR提取流水线',
  '处理 zip 或 pdf 扫描件附件，先解压或转换图片，再通过百度 OCR 识别文档类型并提取发票、合同、履约保函、月付款报审表等关键业务字段。',
  '文档处理',
  'filesystem',
  '1.0',
  'codex-agent',
  'description',
  'lime',
  'READY',
  NULL,
  1,
  230,
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1
  FROM `skill_catalog`
  WHERE `runtime_skill_name` = 'doc-ocr-pipeline'
);

UPDATE `skill_catalog`
SET
  `display_name` = '文档OCR提取流水线',
  `description` = '处理 zip 或 pdf 扫描件附件，先解压或转换图片，再通过百度 OCR 识别文档类型并提取发票、合同、履约保函、月付款报审表等关键业务字段。',
  `category` = '文档处理',
  `source` = 'filesystem',
  `version` = '1.0',
  `author` = 'codex-agent',
  `icon` = 'description',
  `icon_color` = 'lime',
  `tool_binding_status` = 'READY',
  `tool_binding_message` = NULL,
  `tool_binding_details` = NULL,
  `visible` = 1,
  `sort_order` = 230,
  `updated_at` = NOW()
WHERE `runtime_skill_name` = 'doc-ocr-pipeline';

INSERT INTO `agent_template_skill_binding` (`template_id`, `skill_id`, `sort_order`, `created_at`)
SELECT at.`id`, sc.`id`, 230, NOW()
FROM `agent_template` at
JOIN `skill_catalog` sc
  ON sc.`runtime_skill_name` = 'doc-ocr-pipeline'
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
SET atsb.`sort_order` = 230
WHERE at.`agent_code` = 'general-assistant'
  AND sc.`runtime_skill_name` = 'doc-ocr-pipeline';
