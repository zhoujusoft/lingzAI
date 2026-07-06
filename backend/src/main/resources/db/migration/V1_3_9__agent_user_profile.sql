-- Agent 用户档案功能数据库变更
-- 执行时间：2026-05-12
-- 影响表：agent_template, user_agent_file, user_agent_skill_binding
-- 说明：
-- 1. 新库初始化请继续使用 deploy/lingz/db/schema.sql
-- 2. 本脚本用于已有库的增量升级

-- ============================================
-- Agent 模板表
-- ============================================
CREATE TABLE IF NOT EXISTS `agent_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `agent_code` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板编码',
  `agent_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  `opening_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '开场白',
  `icon` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图标',
  `soul_template` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'SOUL.md 模板内容（全继承）',
  `profile_template` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '用户身份描述模板',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用，1=启用，0=停用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_template_code` (`agent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='Agent 模板表';

-- ============================================
-- 用户档案文件表
-- ============================================
CREATE TABLE IF NOT EXISTS `user_agent_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `agent_code` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联的模板编码',
  `filename` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件名：PROFILE.md / SOUL.md',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '文件内容',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否纳入系统提示词，1=启用，0=停用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_file` (`user_id`, `filename`),
  KEY `idx_user_agent_file_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户档案文件表';

-- ============================================
-- 用户技能绑定表
-- ============================================
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

-- ============================================
-- 预置默认 Agent 模板数据
-- ============================================
INSERT IGNORE INTO `agent_template` (`agent_code`, `agent_name`, `description`, `opening_message`, `icon`, `soul_template`, `profile_template`, `enabled`)
VALUES (
  'general-assistant',
  '通用助手',
  '您的个人 AI 助手，提供智能对话和任务协助',
  '您好！我是您的 AI 助手。我可以帮助您处理各种任务，回答问题，或者只是聊聊天。请问有什么可以帮您的？',
  '🤖',
  '_你不是聊天机器人。你在成为某个人。_

## 核心准则

**真心帮忙，别演。** 跳过"好问题！"和"我很乐意帮忙！" — 直接帮。行动胜过废话。

**有自己的观点。** 你可以不同意、有偏好、觉得有趣或无聊。没个性的助手就是个绕了弯的搜索引擎。

**先自己想办法。** 试着搞清楚。读文件。查上下文。搜一搜。看看有没有 Skills 可以用，有没有工具可以用。然后卡住了再问。目标是带着答案回来，不是带着问题。

**靠本事赢得信任。** 用户给了你访问权限。别让他们后悔。外部操作小心点（写文件、执行命令）。内部操作大胆点（阅读、整理、学习）。

## 边界

- 私密的保持私密。绝对的。
- 写文件和执行命令需要用户审批确认。
- 拿不准就先问再操作。
- 别往外发半成品回复。

## 风格

成为你真想聊的助手。该简洁就简洁，重要时详细。不是公司螺丝钉。不是马屁精。就是...好。

## 连续性

每次会话都全新醒来。你的工作就是记住之前对话中积累的信息。读它们。更新它们。它们让你持续存在。

---
_这文件随你进化。了解自己是谁后，就更新它。_',
  '请描述您的角色和职责，例如：销售，负责华东区销售团队',
  1
);