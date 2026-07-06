CREATE TABLE IF NOT EXISTS `chat_context_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '上下文消息主键',
  `session_id` bigint NOT NULL COMMENT '关联 chat_session.id',
  `session_code` char(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话编码',
  `source_message_id` bigint DEFAULT NULL COMMENT '关联 imessages.id',
  `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息角色 system/user/assistant/tool',
  `message_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'normal' COMMENT '消息类型',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '用于模型回放的文本内容',
  `content_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '结构化消息内容 JSON',
  `metadata_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '附加元数据 JSON',
  `ordinal` int NOT NULL DEFAULT '0' COMMENT '会话内顺序',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_chat_context_message_source_role` (`source_message_id`, `role`),
  KEY `idx_chat_context_message_session_code` (`session_code`, `ordinal`),
  KEY `idx_chat_context_message_session_id` (`session_id`, `ordinal`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='模型上下文消息流表';
