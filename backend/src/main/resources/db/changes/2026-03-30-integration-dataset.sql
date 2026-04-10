CREATE TABLE IF NOT EXISTS `integration_data_source` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL COMMENT '数据源名称',
  `alias` varchar(128) DEFAULT NULL COMMENT '别名',
  `db_type` varchar(64) NOT NULL COMMENT '数据库类型',
  `connection_uri` varchar(1024) NOT NULL COMMENT '连接串',
  `auth_type` varchar(64) NOT NULL DEFAULT 'USERNAME_PASSWORD' COMMENT '鉴权类型',
  `auth_config_json` longtext COMMENT '鉴权配置 JSON',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_data_source_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 平台数据源';

CREATE TABLE IF NOT EXISTS `integration_dataset` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dataset_code` varchar(64) DEFAULT NULL COMMENT '数据集编码',
  `name` varchar(128) NOT NULL COMMENT '数据集名称',
  `source_kind` varchar(32) NOT NULL COMMENT '来源类型 AI_SOURCE/LOWCODE_APP',
  `ai_data_source_id` bigint DEFAULT NULL COMMENT 'AI 平台数据源 ID',
  `lowcode_platform_key` varchar(128) DEFAULT NULL COMMENT '低代码平台 key',
  `lowcode_app_id` varchar(128) DEFAULT NULL COMMENT '低代码应用 ID',
  `lowcode_app_name` varchar(255) DEFAULT NULL COMMENT '低代码应用名称',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `business_logic` longtext COMMENT '业务逻辑说明',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_dataset_code` (`dataset_code`),
  KEY `idx_integration_dataset_source_kind` (`source_kind`),
  KEY `idx_integration_dataset_ai_source` (`ai_data_source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一数据集';

CREATE TABLE IF NOT EXISTS `integration_dataset_object_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dataset_id` bigint NOT NULL COMMENT '数据集 ID',
  `object_code` varchar(128) NOT NULL COMMENT '对象编码',
  `form_code` varchar(128) DEFAULT NULL COMMENT '低代码菜单编码',
  `object_name` varchar(255) NOT NULL COMMENT '对象名称',
  `object_source` varchar(255) DEFAULT NULL COMMENT '对象来源',
  `selected` tinyint NOT NULL DEFAULT 1 COMMENT '是否选择',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_dataset_object_binding_dataset` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据集对象绑定';

CREATE TABLE IF NOT EXISTS `integration_dataset_field_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dataset_id` bigint NOT NULL COMMENT '数据集 ID',
  `object_code` varchar(128) NOT NULL COMMENT '对象编码',
  `form_code` varchar(128) DEFAULT NULL COMMENT '低代码菜单编码',
  `field_name` varchar(128) NOT NULL COMMENT '字段名',
  `field_alias` varchar(255) DEFAULT NULL COMMENT '字段别名',
  `field_type` varchar(128) DEFAULT NULL COMMENT '字段类型',
  `field_scope` varchar(64) DEFAULT NULL COMMENT '字段范围',
  `sub_object_code` varchar(128) DEFAULT NULL COMMENT '子对象编码',
  `sub_object_name` varchar(255) DEFAULT NULL COMMENT '子对象名称',
  `object_name` varchar(255) DEFAULT NULL COMMENT '对象名称',
  `selected` tinyint NOT NULL DEFAULT 1 COMMENT '是否选择',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_dataset_field_binding_dataset` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据集字段绑定';

CREATE TABLE IF NOT EXISTS `integration_dataset_relation_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dataset_id` bigint NOT NULL COMMENT '数据集 ID',
  `left_object_code` varchar(128) NOT NULL COMMENT '左对象编码',
  `left_field_name` varchar(128) NOT NULL COMMENT '左字段',
  `right_object_code` varchar(128) NOT NULL COMMENT '右对象编码',
  `right_field_name` varchar(128) NOT NULL COMMENT '右字段',
  `relation_source` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '关系来源 API/MANUAL',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_dataset_relation_binding_dataset` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据集关系绑定';

CREATE TABLE IF NOT EXISTS `integration_dataset_publish_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dataset_id` bigint NOT NULL COMMENT '数据集 ID',
  `publish_status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态',
  `published_tool_codes` varchar(1000) DEFAULT NULL COMMENT '已发布工具编码列表',
  `published_version` int NOT NULL DEFAULT 0 COMMENT '发布版本',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `last_compiled_at` datetime DEFAULT NULL COMMENT '最近编译时间',
  `last_publish_message` varchar(500) DEFAULT NULL COMMENT '最近发布摘要',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_dataset_publish_dataset` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据集发布态';
