CREATE TABLE IF NOT EXISTS `channel_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '渠道配置主键',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道名称',
  `channel_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道类型，如weixin/webhook/dingtalk',
  `route_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '路由类型 GENERAL_CHAT/SKILL_CHAT/DATASET_CHAT',
  `route_target_id` bigint DEFAULT NULL COMMENT '路由目标ID；技能路由为skillId，数据集路由为datasetId，通用聊天为空',
  `owner_user_id` bigint NOT NULL COMMENT '渠道归属的系统用户ID，外部消息将以该用户身份写入内部会话',
  `bot_prefix` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '机器人触发前缀',
  `config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '渠道原始配置JSON',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_channel_config_type_enabled` (`channel_type`, `enabled`),
  KEY `idx_channel_config_owner_user` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外部渠道配置表';

CREATE TABLE IF NOT EXISTS `channel_session_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '绑定主键',
  `channel_id` bigint NOT NULL COMMENT '关联渠道ID',
  `channel_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道类型',
  `external_session_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '外部会话唯一键',
  `external_sender_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '外部发送者ID',
  `external_sender_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '外部发送者名称',
  `reply_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '回复目标标识',
  `chat_session_code` char(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '内部聊天会话编码',
  `last_active_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近活跃时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_session_binding` (`channel_id`, `external_session_key`),
  KEY `idx_channel_session_binding_session_code` (`chat_session_code`),
  KEY `idx_channel_session_binding_channel_type` (`channel_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外部渠道会话与内部聊天会话绑定表';
