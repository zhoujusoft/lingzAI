-- 角色管理数据库变更脚本
-- 执行时间：2026-05-11
-- 影响表：agent_profile, sys_role, agent_skill_binding, t_user
-- 说明：
-- 1. 新库初始化请继续使用 deploy/lingz/db/schema.sql
-- 2. 本脚本用于已有库的增量升级

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

-- 检查并添加 role_id 列
SET @role_id_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'role_id');
SET @add_role_id := IF(@role_id_exists = 0, 'ALTER TABLE `t_user` ADD COLUMN `role_id` bigint DEFAULT NULL COMMENT ''角色 ID'' AFTER `state`', 'SELECT 1');
PREPARE stmt FROM @add_role_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 检查并添加 role_id 索引
SET @role_idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND INDEX_NAME = 'idx_t_user_role_id');
SET @add_role_idx := IF(@role_idx_exists = 0, 'ALTER TABLE `t_user` ADD KEY `idx_t_user_role_id` (`role_id`)', 'SELECT 1');
PREPARE stmt FROM @add_role_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================
-- 预设数据：Agent 和角色初始化
-- ============================================

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

-- 更新已存在的角色名称（幂等性：无论执行多少次结果一致）
UPDATE `sys_role` SET `role_name` = '领导' WHERE `role_code` = 'sales-leader';
