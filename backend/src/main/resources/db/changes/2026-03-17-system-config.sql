-- 配置管理数据库变更脚本
-- 执行时间：2026-03-17
-- 影响表：system_config
-- 说明：
-- 1. 新库初始化请继续使用 backend/src/main/resources/db/schema.sql
-- 2. 本脚本用于已有库的增量升级

CREATE TABLE IF NOT EXISTS `system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置主键',
  `config_key` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置唯一标识',
  `config_value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '配置值，支持 JSON 或普通字符串',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=启用，0=停用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='系统配置表';
