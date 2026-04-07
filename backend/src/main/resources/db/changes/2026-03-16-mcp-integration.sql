-- MCP 集成数据库变更脚本
-- 执行时间：2026-03-16
-- 影响表：tool_catalog, mcp_server
-- 说明：
-- 1. 新库初始化请继续使用 backend/src/main/resources/db/schema.sql
-- 2. 本脚本用于已有库的增量升级

-- ============================================
-- 1. tool_catalog 表增量维护
-- ============================================

ALTER TABLE `tool_catalog`
MODIFY COLUMN `tool_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL
COMMENT '工具类型：GLOBAL/SKILL_NATIVE/MCP_REMOTE';

-- ============================================
-- 2. 新增 mcp_server 表
-- ============================================

CREATE TABLE IF NOT EXISTS `mcp_server` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'MCP server 主键',
  `server_key` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '稳定唯一标识，用于生成工具名前缀',
  `display_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '展示名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '描述',
  `transport_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '传输类型：STREAMABLE_HTTP/SSE',
  `endpoint` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '远程 MCP endpoint 或 base URL',
  `auth_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'NONE' COMMENT '鉴权类型：NONE/BEARER_TOKEN',
  `auth_config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '鉴权配置 JSON',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用，1=启用，0=停用',
  `last_refresh_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'IDLE' COMMENT '最近刷新状态',
  `last_refresh_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最近刷新摘要',
  `last_refreshed_at` datetime DEFAULT NULL COMMENT '最近刷新时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mcp_server_key` (`server_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='远程 MCP server 配置表';
