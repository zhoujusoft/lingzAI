-- 角色资源权限表
-- 用于配置角色对技能和工具的访问权限

CREATE TABLE IF NOT EXISTS `sys_role_resource_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `resource_type` varchar(32) NOT NULL COMMENT '资源类型：SKILL/TOOL',
  `resource_id` bigint NOT NULL COMMENT '资源ID（skill_catalog.id 或 tool_catalog.id）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_resource` (`role_id`,`resource_type`,`resource_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_resource` (`resource_type`,`resource_id`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色资源权限表';
