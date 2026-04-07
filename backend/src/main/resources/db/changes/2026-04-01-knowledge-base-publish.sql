CREATE TABLE IF NOT EXISTS `knowledge_base_publish_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kb_id` bigint NOT NULL COMMENT '知识库 ID',
  `publish_status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态',
  `published_tool_codes` varchar(1000) DEFAULT NULL COMMENT '已发布工具编码列表',
  `published_version` int NOT NULL DEFAULT 0 COMMENT '发布版本',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `last_compiled_at` datetime DEFAULT NULL COMMENT '最近编译时间',
  `last_publish_message` varchar(500) DEFAULT NULL COMMENT '最近发布摘要',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_base_publish_kb` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识库发布态';
