ALTER TABLE `knowledge_base`
    ADD COLUMN `kb_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '知识库稳定编码' AFTER `kb_id`;

UPDATE `knowledge_base`
SET `kb_code` = CONCAT('KB', LPAD(`kb_id`, 8, '0'))
WHERE `kb_code` IS NULL OR TRIM(`kb_code`) = '';

ALTER TABLE `knowledge_base`
    MODIFY COLUMN `kb_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '知识库稳定编码';

ALTER TABLE `knowledge_base`
    ADD UNIQUE KEY `uk_knowledge_base_code` (`kb_code`);

UPDATE `tool_catalog` t
JOIN `integration_dataset` d ON t.`source` = CONCAT('dataset:', d.`id`)
SET t.`source` = CONCAT('dataset:', d.`dataset_code`),
    t.`tool_name` = CASE
        WHEN t.`tool_name` = CONCAT('dataset.', d.`id`, '.search_dataset_summary')
            THEN CONCAT('dataset.', d.`dataset_code`, '.search_dataset_summary')
        WHEN t.`tool_name` = CONCAT('dataset.', d.`id`, '.get_dataset_schema')
            THEN CONCAT('dataset.', d.`dataset_code`, '.get_dataset_schema')
        WHEN t.`tool_name` = CONCAT('dataset.', d.`id`, '.execute_dataset_sql')
            THEN CONCAT('dataset.', d.`dataset_code`, '.execute_dataset_sql')
        ELSE t.`tool_name`
    END
WHERE d.`dataset_code` IS NOT NULL AND TRIM(d.`dataset_code`) <> '';

UPDATE `skill_tool_binding` stb
JOIN `integration_dataset` d ON stb.`tool_name` = CONCAT('dataset.', d.`id`, '.search_dataset_summary')
SET stb.`tool_name` = CONCAT('dataset.', d.`dataset_code`, '.search_dataset_summary')
WHERE d.`dataset_code` IS NOT NULL AND TRIM(d.`dataset_code`) <> '';

UPDATE `skill_tool_binding` stb
JOIN `integration_dataset` d ON stb.`tool_name` = CONCAT('dataset.', d.`id`, '.get_dataset_schema')
SET stb.`tool_name` = CONCAT('dataset.', d.`dataset_code`, '.get_dataset_schema')
WHERE d.`dataset_code` IS NOT NULL AND TRIM(d.`dataset_code`) <> '';

UPDATE `skill_tool_binding` stb
JOIN `integration_dataset` d ON stb.`tool_name` = CONCAT('dataset.', d.`id`, '.execute_dataset_sql')
SET stb.`tool_name` = CONCAT('dataset.', d.`dataset_code`, '.execute_dataset_sql')
WHERE d.`dataset_code` IS NOT NULL AND TRIM(d.`dataset_code`) <> '';

UPDATE `integration_dataset_publish_binding` pb
JOIN `integration_dataset` d ON pb.`dataset_id` = d.`id`
SET pb.`published_tool_codes` = CONCAT(
    'dataset.', d.`dataset_code`, '.search_dataset_summary,',
    'dataset.', d.`dataset_code`, '.get_dataset_schema,',
    'dataset.', d.`dataset_code`, '.execute_dataset_sql')
WHERE d.`dataset_code` IS NOT NULL AND TRIM(d.`dataset_code`) <> '';

UPDATE `tool_catalog` t
JOIN `knowledge_base` kb ON t.`source` = CONCAT('knowledge_base:', kb.`kb_id`)
SET t.`source` = CONCAT('knowledge_base:', kb.`kb_code`),
    t.`tool_name` = CASE
        WHEN t.`tool_name` = CONCAT('knowledge_base.', kb.`kb_id`, '.search')
            THEN CONCAT('knowledge_base.', kb.`kb_code`, '.search')
        ELSE t.`tool_name`
    END
WHERE kb.`kb_code` IS NOT NULL AND TRIM(kb.`kb_code`) <> '';

UPDATE `skill_tool_binding` stb
JOIN `knowledge_base` kb ON stb.`tool_name` = CONCAT('knowledge_base.', kb.`kb_id`, '.search')
SET stb.`tool_name` = CONCAT('knowledge_base.', kb.`kb_code`, '.search')
WHERE kb.`kb_code` IS NOT NULL AND TRIM(kb.`kb_code`) <> '';

UPDATE `knowledge_base_publish_binding` pb
JOIN `knowledge_base` kb ON pb.`kb_id` = kb.`kb_id`
SET pb.`published_tool_codes` = CONCAT('knowledge_base.', kb.`kb_code`, '.search')
WHERE kb.`kb_code` IS NOT NULL AND TRIM(kb.`kb_code`) <> '';
