-- AgentTemplate 主链路合并迁移
-- 执行时间：2026-05-13
-- 影响表：t_user, sys_role, agent_template, agent_template_skill_binding, user_agent_file, user_agent_skill_binding
-- 说明：
-- 1. 为避免 Flyway checksum 漂移，V1_3_8 / V1_3_9 保持不变。
-- 2. 本脚本幂等覆盖 V1_3_8 / V1_3_9 / V1_3_10 的 Agent 主链路最终状态。
-- 3. 新库初始化请继续使用 deploy/lingz/db/schema.sql

-- ============================================
-- t_user 角色字段
-- ============================================
SET @role_id_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_user'
    AND COLUMN_NAME = 'role_id'
);
SET @add_role_id := IF(
  @role_id_exists = 0,
  'ALTER TABLE `t_user` ADD COLUMN `role_id` bigint DEFAULT NULL COMMENT ''角色 ID'' AFTER `state`',
  'SELECT 1'
);
PREPARE stmt FROM @add_role_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @role_idx_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_user'
    AND INDEX_NAME = 'idx_t_user_role_id'
);
SET @add_role_idx := IF(
  @role_idx_exists = 0,
  'ALTER TABLE `t_user` ADD KEY `idx_t_user_role_id` (`role_id`)',
  'SELECT 1'
);
PREPARE stmt FROM @add_role_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================
-- 最终表结构
-- ============================================
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色主键',
  `role_code` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色唯一编码',
  `role_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '角色描述',
  `agent_id` bigint DEFAULT NULL COMMENT '绑定 Agent 模板 ID，可空',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用，1=启用，0=停用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`role_code`),
  KEY `idx_sys_role_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='业务角色表';

CREATE TABLE IF NOT EXISTS `agent_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `agent_code` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板编码',
  `agent_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  `opening_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '开场白',
  `icon` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图标',
  `soul_template` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'SOUL.md 模板内容',
  `profile_template` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'PROFILE.md 模板内容',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用，1=启用，0=停用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_template_code` (`agent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='Agent 模板表';

CREATE TABLE IF NOT EXISTS `agent_template_skill_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_id` bigint NOT NULL COMMENT '模板ID',
  `skill_id` bigint NOT NULL COMMENT '技能ID',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_skill` (`template_id`, `skill_id`),
  KEY `idx_template_skill_sort` (`template_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='模板技能绑定表';

CREATE TABLE IF NOT EXISTS `user_agent_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `agent_code` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联模板编码',
  `filename` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件名：PROFILE.md / SOUL.md',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '文件内容',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否纳入系统提示词，1=启用，0=停用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_file` (`user_id`, `filename`),
  KEY `idx_user_agent_file_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户档案文件表';

CREATE TABLE IF NOT EXISTS `user_agent_skill_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `skill_id` bigint NOT NULL COMMENT '技能ID',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_skill` (`user_id`, `skill_id`),
  KEY `idx_user_skill_sort` (`user_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户技能绑定表';

ALTER TABLE `sys_role`
  MODIFY COLUMN `agent_id` bigint DEFAULT NULL COMMENT '绑定 Agent 模板 ID，可空';

-- ============================================
-- 从旧 AgentProfile 迁移
-- ============================================
SET @agent_profile_exists := (
  SELECT COUNT(*)
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'agent_profile'
);

SET @agent_skill_binding_exists := (
  SELECT COUNT(*)
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'agent_skill_binding'
);

SET @migrate_agent_templates := IF(
  @agent_profile_exists = 1,
  'INSERT INTO `agent_template` (`agent_code`, `agent_name`, `description`, `opening_message`, `icon`, `soul_template`, `profile_template`, `enabled`)
   SELECT `agent_code`, `agent_name`, `description`, `opening_message`, NULL, `system_role_prompt`, NULL, `enabled`
   FROM `agent_profile`
   ON DUPLICATE KEY UPDATE
     `agent_name` = VALUES(`agent_name`),
     `description` = VALUES(`description`),
     `opening_message` = VALUES(`opening_message`),
     `enabled` = VALUES(`enabled`),
     `soul_template` = CASE
         WHEN `agent_template`.`soul_template` IS NULL OR `agent_template`.`soul_template` = '''' THEN VALUES(`soul_template`)
         ELSE `agent_template`.`soul_template`
     END',
  'SELECT 1'
);
PREPARE stmt FROM @migrate_agent_templates; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @migrate_template_skill_bindings := IF(
  @agent_profile_exists = 1 AND @agent_skill_binding_exists = 1,
  'INSERT INTO `agent_template_skill_binding` (`template_id`, `skill_id`, `sort_order`)
   SELECT at.`id`, asb.`skill_id`, asb.`sort_order`
   FROM `agent_skill_binding` asb
   JOIN `agent_profile` ap ON ap.`id` = asb.`agent_id`
   JOIN `agent_template` at ON at.`agent_code` = ap.`agent_code`
   ON DUPLICATE KEY UPDATE
     `sort_order` = VALUES(`sort_order`)',
  'SELECT 1'
);
PREPARE stmt FROM @migrate_template_skill_bindings; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @migrate_role_bindings := IF(
  @agent_profile_exists = 1,
  'UPDATE `sys_role` sr
   JOIN `agent_profile` ap ON ap.`id` = sr.`agent_id`
   JOIN `agent_template` at ON at.`agent_code` = ap.`agent_code`
   SET sr.`agent_id` = at.`id`
   WHERE sr.`agent_id` IS NOT NULL',
  'SELECT 1'
);
PREPARE stmt FROM @migrate_role_bindings; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================
-- 默认模板与默认角色
-- ============================================
INSERT INTO `agent_template` (
  `agent_code`, `agent_name`, `description`, `opening_message`, `icon`, `soul_template`, `profile_template`, `enabled`
)
VALUES
('general-assistant', '通用助手', '通用智能助手，适合日常问答、任务协助与信息整理。', '您好，我是您的 AI 助手，请告诉我需要处理的事情。', '🤖', '## 核心要求

- 直接回答问题，少说空话。
- 优先结合上下文和用户当前任务。
- 对不确定的信息明确说明边界。', '请描述您的角色、职责和关注重点，例如：销售经理，负责华东区客户与团队管理。', 1),
('sales-assistant', '销售助手', '销售业务智能助手，支持客户分析、商机跟进与业绩复盘。', '您好，我是销售助手，可以帮您梳理客户、商机和销售数据。', '📈', '## 核心要求

- 站在销售业务视角回答。
- 优先给出客户、机会、进度和风险判断。
- 输出应便于直接跟进执行。', '请描述您的销售岗位、负责区域、目标客户和当前重点商机。', 1),
('leader-assistant', '领导助手', '管理决策智能助手，支持经营分析、报表解读与辅助决策。', '您好，我是领导助手，可以帮您查看经营数据、整理重点风险并形成决策参考。', '🧭', '## 核心要求

- 以经营视角组织信息。
- 先看目标、进度、风险和建议。
- 输出保持简洁、可汇报、可决策。', '请描述您的管理职责、关注指标、团队范围和近期管理重点。', 1)
ON DUPLICATE KEY UPDATE
  `agent_name` = CASE
      WHEN `agent_template`.`agent_name` IS NULL OR `agent_template`.`agent_name` = '' THEN VALUES(`agent_name`)
      ELSE `agent_template`.`agent_name`
  END,
  `description` = CASE
      WHEN `agent_template`.`description` IS NULL OR `agent_template`.`description` = '' THEN VALUES(`description`)
      ELSE `agent_template`.`description`
  END,
  `opening_message` = CASE
      WHEN `agent_template`.`opening_message` IS NULL OR `agent_template`.`opening_message` = '' THEN VALUES(`opening_message`)
      ELSE `agent_template`.`opening_message`
  END,
  `icon` = CASE
      WHEN `agent_template`.`icon` IS NULL OR `agent_template`.`icon` = '' THEN VALUES(`icon`)
      ELSE `agent_template`.`icon`
  END,
  `soul_template` = CASE
      WHEN `agent_template`.`soul_template` IS NULL OR `agent_template`.`soul_template` = '' THEN VALUES(`soul_template`)
      ELSE `agent_template`.`soul_template`
  END,
  `profile_template` = CASE
      WHEN `agent_template`.`profile_template` IS NULL OR `agent_template`.`profile_template` = '' THEN VALUES(`profile_template`)
      ELSE `agent_template`.`profile_template`
  END,
  `enabled` = CASE
      WHEN `agent_template`.`enabled` IS NULL THEN VALUES(`enabled`)
      ELSE `agent_template`.`enabled`
  END;

INSERT IGNORE INTO `sys_role` (`role_code`, `role_name`, `description`, `agent_id`, `enabled`)
SELECT 'sales', '销售', '销售人员角色，可使用销售助手进行客户管理和业绩跟踪', `id`, 1
FROM `agent_template` WHERE `agent_code` = 'sales-assistant';

INSERT IGNORE INTO `sys_role` (`role_code`, `role_name`, `description`, `agent_id`, `enabled`)
SELECT 'sales-leader', '领导', '销售管理者角色，可使用领导助手进行团队管理和数据分析', `id`, 1
FROM `agent_template` WHERE `agent_code` = 'leader-assistant';

UPDATE `sys_role`
SET `role_name` = '领导'
WHERE `role_code` = 'sales-leader'
  AND (`role_name` IS NULL OR `role_name` = '' OR `role_name` = '销售领导');

-- ============================================
-- 清理旧表
-- ============================================
DROP TABLE IF EXISTS `agent_skill_binding`;
DROP TABLE IF EXISTS `agent_profile`;
