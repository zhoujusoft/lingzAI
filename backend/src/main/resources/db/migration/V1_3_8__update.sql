-- ============================================
-- 角色管理数据库变更（原 V1.3.6.2）
-- ============================================

CREATE TABLE IF NOT EXISTS `agent_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Agent 主键',
  `agent_code` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Agent 唯一编码',
  `agent_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Agent 名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Agent 描述',
  `opening_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '开场白，单语言 Markdown 字符串',
  `system_role_prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '预设角色描述，面向大模型的人设定义，单语言 Markdown 字符串',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用，1=启用，0=停用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_profile_code` (`agent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='业务 Agent 配置表';

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色主键',
  `role_code` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色唯一编码',
  `role_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '角色描述',
  `agent_id` bigint DEFAULT NULL COMMENT '绑定 Agent ID，可空',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用，1=启用，0=停用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`role_code`),
  KEY `idx_sys_role_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='业务角色表';

CREATE TABLE IF NOT EXISTS `agent_skill_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '绑定主键',
  `agent_id` bigint NOT NULL COMMENT 'Agent ID',
  `skill_id` bigint NOT NULL COMMENT '技能目录 ID',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_skill_binding` (`agent_id`,`skill_id`),
  KEY `idx_agent_skill_sort` (`agent_id`,`sort_order`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='Agent 技能绑定表';

-- 检查并添加 role_id 列到 t_user
SET @role_id_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'role_id');
SET @add_role_id := IF(@role_id_exists = 0, 'ALTER TABLE `t_user` ADD COLUMN `role_id` bigint DEFAULT NULL COMMENT ''角色 ID'' AFTER `state`', 'SELECT 1');
PREPARE stmt FROM @add_role_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 检查并添加 role_id 索引
SET @role_idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND INDEX_NAME = 'idx_t_user_role_id');
SET @add_role_idx := IF(@role_idx_exists = 0, 'ALTER TABLE `t_user` ADD KEY `idx_t_user_role_id` (`role_id`)', 'SELECT 1');
PREPARE stmt FROM @add_role_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 插入预设 Agent（使用 INSERT IGNORE 避免重复执行报错）
INSERT IGNORE INTO `agent_profile` (`agent_code`, `agent_name`, `description`, `opening_message`, `system_role_prompt`, `enabled`)
VALUES
('sales-assistant', '销售助手', '销售业务智能助手，支持客户分析、商机跟进、业绩统计等场景',
'您好，我是销售助手。我可以帮您：
- 查询客户信息和跟进记录
- 分析销售数据和业绩趋势
- 管理商机和销售漏斗

请告诉我您需要什么帮助？',
'你是销售业务助手。你需要：
- 准确理解销售相关的问题
- 提供清晰的数据和分析结果
- 给出专业的销售建议
- 保持简洁专业的沟通风格',
1),
('leader-assistant', '领导助手', '管理决策智能助手，支持数据报表、经营分析、辅助决策等场景',
'您好，我是领导助手。我可以帮您：
- 汇总团队业绩和经营数据
- 生成分析报表和趋势洞察
- 提供决策参考建议

请告诉我您需要什么帮助？',
'你是管理决策助手。你需要：
- 理解管理者的业务需求
- 提供全面的数据分析和洞察
- 给出客观的决策参考
- 保持严谨专业的沟通风格',
1);

-- 插入预设角色（通过 agent_code 查询 agent_id，确保绑定正确）
INSERT IGNORE INTO `sys_role` (`role_code`, `role_name`, `description`, `agent_id`, `enabled`)
SELECT 'sales', '销售', '销售人员角色，可使用销售助手进行客户管理和业绩跟踪', `id`, 1
FROM `agent_profile` WHERE `agent_code` = 'sales-assistant';

INSERT IGNORE INTO `sys_role` (`role_code`, `role_name`, `description`, `agent_id`, `enabled`)
SELECT 'sales-leader', '领导', '销售管理者角色，可使用领导助手进行团队管理和数据分析', `id`, 1
FROM `agent_profile` WHERE `agent_code` = 'leader-assistant';

-- ============================================
-- V1.3.7 补充：幂等地添加 skill_studio_project 表的列
-- ============================================
SET @dbname = DATABASE();
SET @tablename = 'skill_studio_project';

-- project_hints_json
SET @columnname = 'project_hints_json';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT ''项目级生成提示 JSON''')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- project_constraints_json
SET @columnname = 'project_constraints_json';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT ''项目级生成约束 JSON''')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- tool_bindings_json
SET @columnname = 'tool_bindings_json';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT ''项目工具绑定配置 JSON''')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- tool_settings_digest
SET @columnname = 'tool_settings_digest';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT ''工具设置摘要''')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- last_generated_tool_digest
SET @columnname = 'last_generated_tool_digest';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT ''最近一次成功生成使用的工具设置摘要''')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ============================================
-- V1.3.7 补充：幂等地添加 conversation_message 表的列
-- ============================================
SET @tablename = 'conversation_message';

-- prompt_tokens
SET @columnname = 'prompt_tokens';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` int DEFAULT NULL COMMENT ''提示词token数'' AFTER `artifact_summary_json`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- completion_tokens
SET @columnname = 'completion_tokens';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` int DEFAULT NULL COMMENT ''回复token数'' AFTER `prompt_tokens`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- total_tokens
SET @columnname = 'total_tokens';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` int DEFAULT NULL COMMENT ''总token数'' AFTER `completion_tokens`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- usage_available
SET @columnname = 'usage_available';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` tinyint(1) NOT NULL DEFAULT ''0'' COMMENT ''是否有真实usage'' AFTER `total_tokens`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- llm_call_count
SET @columnname = 'llm_call_count';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` int DEFAULT NULL COMMENT ''本次运行模型调用次数'' AFTER `usage_available`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- tool_call_count
SET @columnname = 'tool_call_count';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` int DEFAULT NULL COMMENT ''本次运行工具调用次数'' AFTER `llm_call_count`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- model_id
SET @columnname = 'model_id';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` bigint DEFAULT NULL COMMENT ''运行时模型ID'' AFTER `tool_call_count`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- model_provider
SET @columnname = 'model_provider';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''运行时模型提供方'' AFTER `model_id`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- model_name
SET @columnname = 'model_name';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''运行时模型名称'' AFTER `model_provider`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- adapter_type
SET @columnname = 'adapter_type';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''运行时模型适配器类型'' AFTER `model_name`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- usage_summary_json
SET @columnname = 'usage_summary_json';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` json DEFAULT NULL COMMENT ''运行token汇总JSON'' AFTER `adapter_type`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ============================================
-- V1.3.7 补充：幂等地创建 conversation_run_usage 表
-- ============================================
CREATE TABLE IF NOT EXISTS `conversation_run_usage` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '运行统计主键',
    `assistant_message_id` bigint NOT NULL COMMENT 'assistant主消息ID',
    `user_message_id` bigint DEFAULT NULL COMMENT '用户主消息ID',
    `session_id` bigint NOT NULL COMMENT '关联会话ID',
    `session_code` char(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话编码',
    `session_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话类型',
    `scope_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '会话作用域类型',
    `scope_id` bigint DEFAULT NULL COMMENT '会话作用域ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `agent_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '统一agent类型',
    `agent_id` bigint DEFAULT NULL COMMENT '统一agent ID',
    `agent_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'agent展示名',
    `runtime_skill_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '运行时技能名',
    `model_id` bigint DEFAULT NULL COMMENT '运行时模型ID',
    `model_provider` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '运行时模型提供方',
    `model_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '运行时模型名称',
    `adapter_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '运行时模型适配器类型',
    `run_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '运行状态',
    `usage_available` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否有真实usage',
    `prompt_tokens` int DEFAULT NULL COMMENT '提示词token数',
    `completion_tokens` int DEFAULT NULL COMMENT '回复token数',
    `total_tokens` int DEFAULT NULL COMMENT '总token数',
    `llm_call_count` int DEFAULT NULL COMMENT '模型调用次数',
    `tool_call_count` int DEFAULT NULL COMMENT '工具调用次数',
    `duration_ms` bigint DEFAULT NULL COMMENT '运行耗时（毫秒）',
    `started_at` datetime DEFAULT NULL COMMENT '运行开始时间',
    `completed_at` datetime DEFAULT NULL COMMENT '运行完成时间',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_run_usage_assistant` (`assistant_message_id`),
    KEY `idx_conversation_run_usage_user_created` (`user_id`,`created_at`,`id`),
    KEY `idx_conversation_run_usage_agent_created` (`agent_type`,`agent_id`,`created_at`,`id`),
    KEY `idx_conversation_run_usage_session_created` (`session_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='会话运行token统计表';

-- enabled_global 列（幂等）
SET @columnname = 'enabled_global';
SET @tablename = 'mcp_server';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE `mcp_server` ADD COLUMN `enabled_global` tinyint NOT NULL DEFAULT ''0'' COMMENT ''是否全局可用，1=全局，0=非全局'' AFTER `enabled`'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- headers_json 列（幂等）
SET @columnname = 'headers_json';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE `mcp_server` ADD COLUMN `headers_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT ''自定义请求头 JSON'' AFTER `auth_config_json`'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

