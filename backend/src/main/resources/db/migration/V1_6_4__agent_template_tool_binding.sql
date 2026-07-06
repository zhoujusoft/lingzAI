CREATE TABLE IF NOT EXISTS `agent_template_tool_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_id` bigint NOT NULL COMMENT '模板ID',
  `tool_id` bigint NOT NULL COMMENT '工具ID',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_tool` (`template_id`, `tool_id`),
  KEY `idx_template_tool_sort` (`template_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='模板工具绑定表';
