ALTER TABLE `tool_catalog`
    MODIFY COLUMN `tool_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工具类型：GLOBAL/RUNTIME/SKILL_NATIVE/MCP_REMOTE/LOWCODE_API/DATASET_TOOL/KNOWLEDGE_BASE_TOOL';

UPDATE `tool_catalog`
SET `tool_type` = 'RUNTIME',
    `bindable` = 0
WHERE `tool_name` IN ('get_render_template', 'build_frontend_render_payload', 'generate_frontend_render');
