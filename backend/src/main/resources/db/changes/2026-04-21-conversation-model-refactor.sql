DROP TABLE IF EXISTS `conversation_event`;
DROP TABLE IF EXISTS `conversation_message`;
DROP TABLE IF EXISTS `conversation_session`;

DROP TABLE IF EXISTS `chat_context_message`;
DROP TABLE IF EXISTS `imessages`;
DROP TABLE IF EXISTS `chat_session`;

CREATE TABLE `conversation_session` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话主键',
    `session_code` char(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话编码（ULID）',
    `session_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话类型：GENERAL_CHAT/SKILL_CHAT/PUBLISHED_SKILL_CHAT/DATASET_CHAT/KNOWLEDGE_QA',
    `scope_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '作用域类型，例如SKILL/DATASET/KNOWLEDGE_BASE',
    `scope_id` bigint DEFAULT NULL COMMENT '会话作用域ID',
    `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '会话标题',
    `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE' COMMENT '会话状态',
    `last_message_id` bigint DEFAULT NULL COMMENT '最近一条主消息ID',
    `last_message_preview` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最近一条主消息预览',
    `create_user_id` bigint NOT NULL COMMENT '创建用户ID',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_session_code` (`session_code`),
    KEY `idx_conversation_session_user_type_updated` (`create_user_id`,`session_type`,`updated_at`,`id`),
    KEY `idx_conversation_session_scope` (`scope_type`,`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='会话主表';

CREATE TABLE `conversation_message` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息主键',
    `session_id` bigint NOT NULL COMMENT '关联会话ID',
    `message_code` char(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息编码（ULID）',
    `parent_message_id` bigint DEFAULT NULL COMMENT '父消息ID，用于一条用户消息关联多条assistant消息',
    `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息角色 USER/ASSISTANT/SYSTEM',
    `message_kind` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息类型 INPUT/REPLY/FOLLOWUP/NOTICE/SUMMARY/ARTIFACT_CARD',
    `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '主展示内容',
    `content_format` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'TEXT' COMMENT '内容格式 TEXT/MARKDOWN/JSON',
    `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT '消息状态 PENDING/STREAMING/COMPLETED/FAILED/CANCELLED',
    `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误码',
    `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '错误信息',
    `params_json` json DEFAULT NULL COMMENT '扩展参数JSON',
    `attachments_json` json DEFAULT NULL COMMENT '附件JSON',
    `artifact_summary_json` json DEFAULT NULL COMMENT '产物/引用摘要JSON',
    `sequence_no` int NOT NULL COMMENT '会话内消息顺序',
    `create_user_id` bigint DEFAULT NULL COMMENT '创建用户ID',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_message_code` (`message_code`),
    KEY `idx_conversation_message_session_seq` (`session_id`,`sequence_no`,`id`),
    KEY `idx_conversation_message_parent` (`parent_message_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='会话主消息表';

CREATE TABLE `conversation_event` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '事件主键',
    `session_id` bigint NOT NULL COMMENT '关联会话ID',
    `message_id` bigint DEFAULT NULL COMMENT '关联主消息ID',
    `event_code` char(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '事件编码（ULID）',
    `parent_event_id` bigint DEFAULT NULL COMMENT '父事件ID',
    `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '事件类型',
    `event_subtype` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '事件子类型',
    `sequence_no` int NOT NULL COMMENT '会话内事件顺序',
    `summary_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '用于记忆与折叠展示的摘要',
    `payload_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '结构化事件负载JSON',
    `create_user_id` bigint DEFAULT NULL COMMENT '创建用户ID',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_event_code` (`event_code`),
    KEY `idx_conversation_event_session_seq` (`session_id`,`sequence_no`,`id`),
    KEY `idx_conversation_event_message_type` (`message_id`,`event_type`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='会话事件流表';
