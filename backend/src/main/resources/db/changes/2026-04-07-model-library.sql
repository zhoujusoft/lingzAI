CREATE TABLE IF NOT EXISTS `model_vendor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模型厂商主键',
  `vendor_code` varchar(64) NOT NULL COMMENT '模型厂商编码',
  `vendor_name` varchar(128) NOT NULL COMMENT '模型厂商名称',
  `description` varchar(500) DEFAULT NULL COMMENT '厂商描述',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DRAFT',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_vendor_code` (`vendor_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='模型厂商';

CREATE TABLE IF NOT EXISTS `model_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模型主键',
  `model_code` varchar(64) NOT NULL COMMENT '模型编码',
  `display_name` varchar(128) NOT NULL COMMENT '模型展示名称',
  `capability_type` varchar(32) NOT NULL COMMENT '能力类型：CHAT/EMBEDDING/RERANK',
  `vendor_id` bigint NOT NULL COMMENT '模型厂商 ID',
  `adapter_type` varchar(32) NOT NULL COMMENT '适配器类型：QWEN_ONLINE/VLLM',
  `protocol` varchar(32) DEFAULT NULL COMMENT '协议类型',
  `base_url` varchar(500) NOT NULL COMMENT '模型服务 Base URL',
  `api_key` varchar(500) NOT NULL COMMENT '模型密钥',
  `path` varchar(255) DEFAULT NULL COMMENT '接口路径',
  `model_name` varchar(255) NOT NULL COMMENT '实际模型名',
  `temperature` decimal(6,4) DEFAULT NULL COMMENT '对话采样温度',
  `max_tokens` int DEFAULT NULL COMMENT '对话最大输出 token',
  `system_prompt` longtext COMMENT '模型默认系统提示词',
  `enable_thinking` tinyint(1) DEFAULT NULL COMMENT '是否开启 thinking',
  `dimensions` int DEFAULT NULL COMMENT '向量维度',
  `timeout_ms` int DEFAULT NULL COMMENT '超时时间（毫秒）',
  `fallback_rrf` tinyint(1) DEFAULT NULL COMMENT 'rerank 失败时是否回退 RRF',
  `extra_config_json` longtext COMMENT '扩展配置 JSON',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DRAFT',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_definition_code` (`model_code`),
  KEY `idx_model_definition_vendor` (`vendor_id`),
  KEY `idx_model_definition_capability_status` (`capability_type`,`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='模型定义';

CREATE TABLE IF NOT EXISTS `model_default_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '默认模型绑定主键',
  `capability_type` varchar(32) NOT NULL COMMENT '能力类型：CHAT/EMBEDDING/RERANK',
  `model_id` bigint NOT NULL COMMENT '默认模型 ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_default_binding_capability` (`capability_type`),
  KEY `idx_model_default_binding_model` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='默认模型绑定';
