-- MCP server 新增内部/外部范围字段
-- 手动执行：为外部平台型 MCP（如无状态 streamable-http）提供适配入口

ALTER TABLE `mcp_server`
ADD COLUMN `server_scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'INTERNAL' COMMENT '服务范围：INTERNAL/EXTERNAL'
AFTER `description`;
