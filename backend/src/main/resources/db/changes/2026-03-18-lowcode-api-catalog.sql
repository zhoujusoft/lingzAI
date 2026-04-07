-- 任务：03-18-lowcode-api-tools
-- 说明：新增低代码平台 API 目录表
-- 影响表：lowcode_api_catalog

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `lowcode_api_catalog` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '低代码 API 目录主键',
  `platform_key` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '低代码平台标识',
  `app_id` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '应用 ID',
  `app_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '应用名称',
  `api_id` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '远程 API ID',
  `api_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '远程 API Code',
  `api_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API 名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'API 描述',
  `remote_schema_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '远程原始 schema JSON',
  `tool_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '注册后的工具名称',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用，1=启用，0=停用',
  `last_sync_at` datetime DEFAULT NULL COMMENT '最近同步时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lowcode_api_catalog_platform_code` (`platform_key`,`api_code`),
  UNIQUE KEY `uk_lowcode_api_catalog_tool_name` (`tool_name`),
  KEY `idx_lowcode_api_catalog_platform_app` (`platform_key`,`app_id`,`id`),
  KEY `idx_lowcode_api_catalog_enabled` (`enabled`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='低代码平台 API 目录表';
