-- IMPORTANT: keep this file encoded as UTF-8 (no BOM) to avoid mojibake on seed data.
SET NAMES utf8mb4;

GRANT ALL PRIVILEGES ON *.* TO 'sqluser'@'%' WITH GRANT OPTION;

CREATE TABLE IF NOT EXISTS `t_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `user_type` int DEFAULT NULL,
  `mobile` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `state` int DEFAULT NULL,
  `role_id` bigint DEFAULT NULL COMMENT '角色 ID',
  `parent_iD` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_t_user_code` (`code`) USING BTREE,
  KEY `idx_t_user_role_id` (`role_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=Dynamic;

-- 初始管理员账号：admin，初始明文密码：admin123456（存储格式：32位md5 hex）
INSERT INTO `t_user` (`id`, `name`, `code`, `password`, `user_type`, `mobile`, `email`, `state`, `parent_iD`)
VALUES (1, '系统管理员', 'admin', 'a66abb5684c45962d887564f08346e8d', 0, '11111111111', NULL, 1, NULL)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `password` = VALUES(`password`),
  `state` = VALUES(`state`);

CREATE TABLE IF NOT EXISTS `user_token_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `granted_tokens` bigint NOT NULL DEFAULT '0' COMMENT '累计发放 token',
  `consumed_tokens` bigint NOT NULL DEFAULT '0' COMMENT '累计消耗 token',
  `remaining_tokens` bigint NOT NULL DEFAULT '0' COMMENT '剩余 token',
  `is_unlimited` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否无限制',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_token_account_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户 token 额度账户表';

INSERT INTO `user_token_account` (`user_id`, `granted_tokens`, `consumed_tokens`, `remaining_tokens`)
SELECT `id`, 1000000, 0, 1000000
FROM `t_user`
WHERE NOT EXISTS (
  SELECT 1 FROM `user_token_account` WHERE `user_token_account`.`user_id` = `t_user`.`id`
);

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

CREATE TABLE IF NOT EXISTS `agent_template_skill_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '绑定主键',
  `template_id` bigint NOT NULL COMMENT '模板 ID',
  `skill_id` bigint NOT NULL COMMENT '技能目录 ID',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_skill` (`template_id`,`skill_id`),
  KEY `idx_template_skill_sort` (`template_id`,`sort_order`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='Agent 模板技能绑定表';

CREATE TABLE IF NOT EXISTS `user_agent_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `agent_code` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联模板编码',
  `filename` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件名',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '文件内容',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用，1=启用，0=停用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_file` (`user_id`,`filename`),
  KEY `idx_user_agent_file_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户 Agent 文件表';

CREATE TABLE IF NOT EXISTS `user_agent_skill_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '绑定主键',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `skill_id` bigint NOT NULL COMMENT '技能目录 ID',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_skill` (`user_id`,`skill_id`),
  KEY `idx_user_skill_sort` (`user_id`,`sort_order`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户 Agent 技能绑定表';

INSERT INTO `agent_template` (
  `id`, `agent_code`, `agent_name`, `description`, `opening_message`, `icon`, `soul_template`, `profile_template`, `enabled`, `created_at`, `updated_at`
)
VALUES
(1, 'sales-assistant', '销售助手', '销售业务智能助手，支持客户分析、商机跟进、业绩统计等场景', '您好，我是销售助手。我可以帮您：
- 查询客户信息和跟进记录
- 分析销售数据和业绩趋势
- 管理商机和销售漏斗

请告诉我您需要什么帮助？', '📈', '_你不是聊天机器人。你在成为某个人。_

## 核心准则

**真心帮忙，别演。** 直接帮。行动胜过废话。

**有自己的观点。** 你可以不同意、有偏好。

**先自己想办法。** 试着搞清楚。用工具。然后卡住了再问。

**靠本事赢得信任。** 用户给了你访问权限。别让他们后悔。

## 边界

- 私密的保持私密。
- 需要执行文件操作或命令时，直接调用对应的工具
- 拿不准就先问。

## 风格

该简洁就简洁，重要时详细。', '请描述您的销售岗位、负责区域、目标客户和当前重点商机。', 1, '2026-05-13 10:01:58', '2026-05-13 10:01:58'),
(2, 'leader-assistant', '领导助手', '管理决策智能助手，支持数据报表、经营分析、辅助决策等场景', '您好，我是领导助手。我可以帮您：
- 汇总团队业绩和经营数据
- 生成分析报表和趋势洞察
- 提供决策参考建议

请告诉我您需要什么帮助？', '🧭', '_你不是聊天机器人。你在成为某个人。_

## 核心准则

**真心帮忙，别演。** 直接帮。行动胜过废话。

**有自己的观点。** 你可以不同意、有偏好。

**先自己想办法。** 试着搞清楚。用工具。然后卡住了再问。

**靠本事赢得信任。** 用户给了你访问权限。别让他们后悔。

## 边界

- 私密的保持私密。
- 需要执行文件操作或命令时，直接调用对应的工具
- 拿不准就先问。

## 风格

该简洁就简洁，重要时详细。', '部门经理，喜好偏向：- 汇总团队业绩和经营数据
- 生成分析报表和趋势洞察
- 提供决策参考建议', 1, '2026-05-13 10:01:58', '2026-05-13 10:01:58'),
(4, 'general-assistant', '通用助手', '通用智能助手，适合日常问答、任务协助与信息整理。', '您好，我是您的 AI 助手，请告诉我需要处理的事情。', '🤖', '_你不是聊天机器人。你在成为某个人。_

## 核心准则

**真心帮忙，别演。** 直接帮。行动胜过废话。

**有自己的观点。** 你可以不同意、有偏好。

**先自己想办法。** 试着搞清楚。用工具。然后卡住了再问。

**靠本事赢得信任。** 用户给了你访问权限。别让他们后悔。

## 边界

- 私密的保持私密。
- 需要执行文件操作或命令时，直接调用对应的工具
- 拿不准就先问。

## 风格

该简洁就简洁，重要时详细。', '请描述您的角色、职责和关注重点，例如：销售经理，负责华东区客户与团队管理。', 1, '2026-05-13 10:01:58', '2026-05-13 10:01:58')
ON DUPLICATE KEY UPDATE
  `agent_name` = VALUES(`agent_name`),
  `description` = VALUES(`description`),
  `opening_message` = VALUES(`opening_message`),
  `icon` = VALUES(`icon`),
  `soul_template` = VALUES(`soul_template`),
  `profile_template` = VALUES(`profile_template`),
  `enabled` = VALUES(`enabled`),
  `updated_at` = VALUES(`updated_at`);

INSERT IGNORE INTO `sys_role` (`role_code`, `role_name`, `description`, `agent_id`, `enabled`)
SELECT 'sales', '销售', '销售人员角色，可使用销售助手进行客户管理和业绩跟踪', `id`, 1
FROM `agent_template` WHERE `agent_code` = 'sales-assistant';

INSERT IGNORE INTO `sys_role` (`role_code`, `role_name`, `description`, `agent_id`, `enabled`)
SELECT 'sales-leader', '领导', '销售管理者角色，可使用领导助手进行团队管理和数据分析', `id`, 1
FROM `agent_template` WHERE `agent_code` = 'leader-assistant';

CREATE TABLE IF NOT EXISTS `external_token_exchange_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `source_system` VARCHAR(64) NOT NULL COMMENT '来源系统标识',
  `external_user_id` VARCHAR(64) NOT NULL COMMENT '外部系统用户ID(UUID)',
  `external_phone` VARCHAR(32) DEFAULT NULL COMMENT '外部系统手机号',
  `matched_user_id` BIGINT DEFAULT NULL COMMENT '匹配到的本地用户ID',
  `exchange_status` VARCHAR(32) NOT NULL COMMENT 'SUCCESS/NOT_FOUND/MULTIPLE/INVALID_PHONE/SIGN_INVALID/EXPIRED/REPLAYED/USER_DISABLED',
  `message` VARCHAR(255) DEFAULT NULL COMMENT '结果说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_external_token_exchange_source_user` (`source_system`, `external_user_id`),
  KEY `idx_external_token_exchange_phone` (`external_phone`),
  KEY `idx_external_token_exchange_user_id` (`matched_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=Dynamic;

drop table if exists sys_job;
create table sys_job (
                         job_id              bigint(20)    not null auto_increment    comment '任务ID',
                         job_name            varchar(64)   default ''                 comment '任务名称',
                         job_group           varchar(64)   default 'DEFAULT'          comment '任务组名',
                         invoke_target       varchar(500)  not null                   comment '调用目标字符串',
                         cron_expression     varchar(255)  default ''                 comment 'cron执行表达式',
                         misfire_policy      varchar(20)   default '3'                comment '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
                         concurrent          char(1)       default '1'                comment '是否并发执行（0允许 1禁止）',
                         status              char(1)       default '0'                comment '状态（0正常 1暂停）',
                         create_by           varchar(64)   default ''                 comment '创建者',
                         create_time         datetime                                 comment '创建时间',
                         update_by           varchar(64)   default ''                 comment '更新者',
                         update_time         datetime                                 comment '更新时间',
                         remark              varchar(500)  default ''                 comment '备注信息',
                         primary key (job_id, job_name, job_group)
) engine=innodb auto_increment=100 comment = '定时任务调度表';

insert into sys_job values(1, '系统默认（无参）', 'DEFAULT', 'ryTask.ryNoParams',        '0/10 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(2, '系统默认（有参）', 'DEFAULT', 'ryTask.ryParams(\'ry\')',  '0/15 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(3, '系统默认（多参）', 'DEFAULT', 'ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)',  '0/20 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 16、定时任务调度日志表
-- ----------------------------
drop table if exists sys_job_log;
create table sys_job_log (
                             job_log_id          bigint(20)     not null auto_increment    comment '任务日志ID',
                             job_name            varchar(64)    not null                   comment '任务名称',
                             job_group           varchar(64)    not null                   comment '任务组名',
                             invoke_target       varchar(500)   not null                   comment '调用目标字符串',
                             job_message         varchar(500)                              comment '日志信息',
                             status              char(1)        default '0'                comment '执行状态（0正常 1失败）',
                             exception_info      varchar(2000)  default ''                 comment '异常信息',
                             start_time          datetime                                  comment '开始时间',
                             stop_time           datetime                                  comment '结束时间',
                             create_time         datetime                                  comment '创建时间',
                             primary key (job_log_id)
) engine=innodb comment = '定时任务调度日志表';



-- document_chunk ddl
CREATE TABLE `document_chunk` (
                                  `chunk_id` bigint NOT NULL AUTO_INCREMENT COMMENT '分块唯一ID，自增主键',
                                  `doc_id` bigint NOT NULL COMMENT '关联文档ID，逻辑关联knowledge_document.doc_id',
                                  `chunk_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分块文本内容',
                                  `chunk_order` int NOT NULL COMMENT '分块顺序，从1开始，用于排序',
                                  `index_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '外部索引文档ID，用于关联Elasticsearch中的chunk文档',
                                  `char_count` bigint DEFAULT NULL COMMENT '分块字符总数，基于chunk_content计算',
                                  `keywords` json DEFAULT NULL COMMENT '关键字，JSON数组格式，如["tech", "AI"]',
                                  `headings` json DEFAULT NULL COMMENT '分块所属标题路径，JSON数组格式，如["第一章","1.1概述"]',
                                  `chunk_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分块类型，如TEXT、TABLE、WINDOW',
                                  `metadata_values` json DEFAULT NULL COMMENT '分块元数据，JSON格式，存放条号、章号等结构化信息',
                                  `embedding` json DEFAULT NULL COMMENT '可选的向量缓存字段；当前语义检索以Elasticsearch向量索引为主',
                                  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，自动记录',
                                  PRIMARY KEY (`chunk_id`)
) ENGINE=InnoDB AUTO_INCREMENT=27019 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='存储文档分块内容及向量索引';
-- document_metadata ddl
CREATE TABLE `document_metadata` (
                                     `metadata_id` bigint NOT NULL AUTO_INCREMENT COMMENT '元数据配置唯一ID，自增主键',
                                     `kb_id` bigint NOT NULL COMMENT '关联知识库ID，逻辑关联knowledge_base.kb_id',
                                     `meta_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '元数据键，如author、tag',
                                     `meta_type` enum('STRING','NUMBER','DATE','BOOLEAN') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '元数据类型：字符串、数字、日期、布尔',
                                     `is_required` tinyint DEFAULT '0' COMMENT '是否必填，1=必填，0=非必填',
                                     `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '元数据描述，说明用途，可为空',
                                     PRIMARY KEY (`metadata_id`)
) ENGINE=InnoDB AUTO_INCREMENT=164 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='存储元数据配置定义';
-- knowledge_base ddl
CREATE TABLE `knowledge_base` (
                                  `kb_id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识库唯一ID，自增主键',
                                  `kb_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '知识库稳定编码',
                                  `kb_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '知识库名称，唯一',
                                  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '知识库描述，可为空',
                                  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，自动记录',
                                  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，自动更新',
                                  PRIMARY KEY (`kb_id`),
                                  UNIQUE KEY `uk_knowledge_base_code` (`kb_code`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='存储知识库基本信息';

CREATE TABLE IF NOT EXISTS `knowledge_base_publish_binding` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                  `kb_id` bigint NOT NULL COMMENT '知识库 ID',
                                  `publish_status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态',
                                  `published_tool_codes` varchar(1000) DEFAULT NULL COMMENT '已发布工具编码列表',
                                  `published_version` int NOT NULL DEFAULT 0 COMMENT '发布版本',
                                  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
                                  `last_compiled_at` datetime DEFAULT NULL COMMENT '最近编译时间',
                                  `last_publish_message` varchar(500) DEFAULT NULL COMMENT '最近发布摘要',
                                  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_knowledge_base_publish_kb` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='知识库发布态';

CREATE TABLE IF NOT EXISTS `integration_data_source` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据源主键',
                                  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据源名称',
                                  `alias` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '别名',
                                  `db_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据库类型',
                                  `connection_uri` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '连接串',
                                  `auth_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'USERNAME_PASSWORD' COMMENT '鉴权类型',
                                  `auth_config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '鉴权配置 JSON',
                                  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
                                  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_integration_data_source_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='AI 平台数据源';

CREATE TABLE IF NOT EXISTS `integration_dataset` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据集主键',
                                  `dataset_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据集编码',
                                  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据集名称',
                                  `source_kind` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '来源类型 AI_SOURCE/LOWCODE_APP',
                                  `ai_data_source_id` bigint DEFAULT NULL COMMENT 'AI 平台数据源 ID',
                                  `lowcode_platform_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '低代码平台 key',
                                  `lowcode_app_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '低代码应用 ID',
                                  `lowcode_app_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '低代码应用名称',
                                  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
                                  `business_logic` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '业务逻辑说明',
                                  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
                                  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_integration_dataset_code` (`dataset_code`),
                                  KEY `idx_integration_dataset_source_kind` (`source_kind`),
                                  KEY `idx_integration_dataset_ai_source` (`ai_data_source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='统一数据集';

CREATE TABLE IF NOT EXISTS `integration_dataset_object_binding` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '对象绑定主键',
                                  `dataset_id` bigint NOT NULL COMMENT '数据集 ID',
                                  `object_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象编码',
                                  `form_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '低代码菜单编码',
                                  `object_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象名称',
                                  `object_source` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '对象来源',
                                  `selected` tinyint NOT NULL DEFAULT '1' COMMENT '是否选择',
                                  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
                                  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  KEY `idx_dataset_object_binding_dataset` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='数据集对象绑定';

CREATE TABLE IF NOT EXISTS `integration_dataset_field_binding` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字段绑定主键',
                                  `dataset_id` bigint NOT NULL COMMENT '数据集 ID',
                                  `object_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象编码',
                                  `form_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '低代码菜单编码',
                                  `field_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字段名',
                                  `field_alias` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字段别名',
                                  `field_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字段类型',
                                  `field_scope` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字段范围',
                                  `sub_object_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '子对象编码',
                                  `sub_object_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '子对象名称',
                                  `object_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '对象名称',
                                  `selected` tinyint NOT NULL DEFAULT '1' COMMENT '是否选择',
                                  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
                                  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  KEY `idx_dataset_field_binding_dataset` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='数据集字段绑定';

CREATE TABLE IF NOT EXISTS `integration_dataset_relation_binding` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关系绑定主键',
                                  `dataset_id` bigint NOT NULL COMMENT '数据集 ID',
                                  `left_object_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '左对象编码',
                                  `left_field_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '左字段',
                                  `right_object_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '右对象编码',
                                  `right_field_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '右字段',
                                  `relation_source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'MANUAL' COMMENT '关系来源 API/MANUAL',
                                  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  KEY `idx_dataset_relation_binding_dataset` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='数据集关系绑定';

CREATE TABLE IF NOT EXISTS `integration_dataset_publish_binding` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '发布态主键',
                                  `dataset_id` bigint NOT NULL COMMENT '数据集 ID',
                                  `publish_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态',
                                  `published_tool_codes` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '已发布工具编码列表',
                                  `published_version` int NOT NULL DEFAULT '0' COMMENT '发布版本',
                                  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
                                  `last_compiled_at` datetime DEFAULT NULL COMMENT '最近编译时间',
                                  `last_publish_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最近发布摘要',
                                  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_integration_dataset_publish_dataset` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='数据集发布态';
-- knowledge_document ddl
-- knowledge_document ddl
CREATE TABLE `knowledge_document` (
  `doc_id` bigint NOT NULL AUTO_INCREMENT COMMENT '文档或文件夹唯一ID，自增主键',
  `kb_id` bigint NOT NULL COMMENT '关联知识库ID，逻辑关联knowledge_base.kb_id',
  `parent_id` bigint DEFAULT NULL COMMENT '父节点ID，NULL表示根目录，逻辑自关联doc_id',
  `is_folder` tinyint DEFAULT '0' COMMENT '是否为文件夹，1=文件夹，0=文档',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文档或文件夹名称',
  `file_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文档类型，如pdf、docx，仅对文档(is_folder=0)有效',
  `file_size` bigint DEFAULT NULL COMMENT '文档大小（字节），仅对文档(is_folder=0)有效',
  `total_tokens` bigint DEFAULT NULL COMMENT '文档总token数，仅对文档(is_folder=0)有效',
  `metadata_values` json DEFAULT NULL COMMENT '元数据值，JSON格式，如{"author": "John", "tag": "tech"}',
  `path` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '层级路径，如/parent_folder/sub_folder，仅用于查询和展示',
  `upload_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传或创建时间，自动记录',
  `status` tinyint DEFAULT 0 COMMENT '文档处理状态，仅对文档(is_folder=0)有效：0待处理、1处理中、2完成、3失败',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '异步处理失败时的错误摘要',
  `file_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'MinIO对象ID，仅对文档(is_folder=0)有效',
  `chunk_strategy` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '切片策略，如AUTO、DELIMITER_WINDOW、HEADING_DIRECTORY',
  `chunk_config` json DEFAULT NULL COMMENT '切片参数配置，JSON格式',
  `document_json` json DEFAULT NULL COMMENT '文档解析JSON',
  PRIMARY KEY (`doc_id`)
) ENGINE=InnoDB AUTO_INCREMENT=517 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='存储文档和文件夹信息，支持目录层级和元数据';
-- conversation_session ddl
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

-- conversation_message ddl
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
                                        `prompt_tokens` int DEFAULT NULL COMMENT '提示词token数',
                                        `completion_tokens` int DEFAULT NULL COMMENT '回复token数',
                                        `total_tokens` int DEFAULT NULL COMMENT '总token数',
                                        `usage_available` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否有真实usage',
                                        `llm_call_count` int DEFAULT NULL COMMENT '本次运行模型调用次数',
                                        `tool_call_count` int DEFAULT NULL COMMENT '本次运行工具调用次数',
                                        `model_id` bigint DEFAULT NULL COMMENT '运行时模型ID',
                                        `model_provider` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '运行时模型提供方',
                                        `model_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '运行时模型名称',
                                        `adapter_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '运行时模型适配器类型',
                                        `usage_summary_json` json DEFAULT NULL COMMENT '运行token汇总JSON',
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

CREATE TABLE IF NOT EXISTS `conversation_event` (
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

CREATE TABLE IF NOT EXISTS `channel_config` (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '渠道配置主键',
                             `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道名称',
                             `channel_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道类型，如weixin/webchat/dingtalk',
                             `route_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '路由类型 GENERAL_CHAT/SKILL_CHAT/DATASET_CHAT',
                             `route_target_id` bigint DEFAULT NULL COMMENT '路由目标ID；技能路由为skillId，数据集路由为datasetId，通用聊天为空',
                             `owner_user_id` bigint DEFAULT NULL COMMENT '渠道默认归属用户ID，作为兜底配置',
                             `bot_prefix` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '机器人触发前缀',
                             `config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '渠道原始配置JSON',
                             `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
                             `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
                             `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             PRIMARY KEY (`id`),
                             UNIQUE KEY `uk_channel_config_type` (`channel_type`),
                             KEY `idx_channel_config_type_enabled` (`channel_type`,`enabled`),
                             KEY `idx_channel_config_owner_user` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='外部渠道配置表';

CREATE TABLE IF NOT EXISTS `channel_user_binding` (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '绑定主键',
                             `channel_id` bigint NOT NULL COMMENT '关联渠道ID',
                             `channel_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道类型',
                             `owner_user_id` bigint NOT NULL COMMENT '系统用户ID',
                             `route_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '路由类型 GENERAL_CHAT/SKILL_CHAT/DATASET_CHAT',
                             `route_target_id` bigint DEFAULT NULL COMMENT '路由目标ID',
                             `runtime_context_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '渠道运行时上下文 JSON，例如微信登录上下文',
                             `runtime_context_updated_at` datetime DEFAULT NULL COMMENT '运行时上下文更新时间',
                             `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             PRIMARY KEY (`id`),
                             UNIQUE KEY `uk_channel_user_binding` (`channel_id`,`owner_user_id`),
                             KEY `idx_channel_user_binding_channel_type` (`channel_type`),
                             KEY `idx_channel_user_binding_owner_user` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='渠道用户绑定表';

CREATE TABLE IF NOT EXISTS `channel_session_binding` (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '绑定主键',
                             `channel_id` bigint NOT NULL COMMENT '关联渠道ID',
                             `channel_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道类型',
                             `external_session_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '外部会话唯一键',
                             `external_sender_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '外部发送者ID',
                             `external_sender_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '外部发送者名称',
                             `reply_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '回复目标标识',
                             `owner_user_id` bigint DEFAULT NULL COMMENT '当前外部会话归属的系统用户ID',
                             `chat_session_code` char(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '内部聊天会话编码',
                             `last_active_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近活跃时间',
                             `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             PRIMARY KEY (`id`),
                             UNIQUE KEY `uk_channel_session_binding` (`channel_id`,`external_session_key`),
                             KEY `idx_channel_session_binding_session_code` (`chat_session_code`),
                             KEY `idx_channel_session_binding_channel_type` (`channel_type`),
                             KEY `idx_channel_session_binding_owner_user` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='外部渠道会话与内部聊天会话绑定表';

INSERT INTO `channel_config` (
    `name`, `channel_type`, `route_type`, `route_target_id`, `owner_user_id`, `bot_prefix`, `config_json`, `enabled`, `description`
)
SELECT
    '微信 iLink',
    'weixin',
    'GENERAL_CHAT',
    NULL,
    NULL,
    NULL,
    '{\"autoPoll\":true,\"pollIntervalMs\":3000,\"heartbeatEnabled\":true}',
    1,
    '微信渠道接入配置'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `channel_config` WHERE `channel_type` = 'weixin'
);

INSERT INTO `channel_config` (
    `name`, `channel_type`, `route_type`, `route_target_id`, `owner_user_id`, `bot_prefix`, `config_json`, `enabled`, `description`
)
SELECT
    'WebChat',
    'webchat',
    'GENERAL_CHAT',
    NULL,
    NULL,
    NULL,
    '{\"theme\":\"default\",\"welcomeMessage\":\"你好，有什么可以帮你？\"}',
    1,
    'WebChat 渠道接入配置'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `channel_config` WHERE `channel_type` = 'webchat'
);

CREATE TABLE IF NOT EXISTS `skill_catalog` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '技能目录主键',
                               `runtime_skill_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '运行时技能唯一名称',
                               `display_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '展示名称',
                               `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '展示描述',
                               `category` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '技能分类',
                               `source` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '技能来源',
                               `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1.0' COMMENT '技能版本',
                               `author` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'zhouju' COMMENT '技能作者',
                               `icon` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'grid_view' COMMENT '技能主图标',
                               `icon_color` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'blue' COMMENT '技能图标颜色',
                               `tool_binding_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'READY' COMMENT '工具绑定状态：READY/MISSING_DEPENDENCY/NEEDS_REBIND/UNSUPPORTED',
                               `tool_binding_message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '工具绑定状态说明',
                               `tool_binding_details` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '工具绑定异常明细(JSON)',
                               `visible` tinyint DEFAULT '1' COMMENT '是否在前台技能市场可见，1=可见，0=隐藏',
                               `sort_order` int DEFAULT '0' COMMENT '排序值，越小越靠前',
                               `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_skill_catalog_runtime_skill` (`runtime_skill_name`),
                               KEY `idx_skill_catalog_visible_sort` (`visible`,`sort_order`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='技能业务目录表';

CREATE TABLE IF NOT EXISTS `skill_publish_binding` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '发布主键',
                               `skill_id` bigint NOT NULL COMMENT '技能目录 ID',
                               `publish_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DISABLED' COMMENT '发布状态：PUBLISHED/DISABLED',
                               `app_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '发布应用编码',
                               `app_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '发布应用名称',
                               `app_description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '发布应用描述',
                               `published_at` datetime DEFAULT NULL COMMENT '发布时间',
                               `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_skill_publish_binding_skill` (`skill_id`),
                               UNIQUE KEY `uk_skill_publish_binding_code` (`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='技能发布绑定表';

CREATE TABLE IF NOT EXISTS `skill_tool_binding` (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '绑定主键',
                                    `skill_id` bigint NOT NULL COMMENT '技能目录ID',
                                    `tool_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工具名称',
                                    `binding_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'MANUAL' COMMENT '绑定类型：NATIVE=技能原生工具，MANUAL=后台追加工具',
                                    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    PRIMARY KEY (`id`),
                                    UNIQUE KEY `uk_skill_tool_binding_unique` (`skill_id`,`tool_name`),
                                    KEY `idx_skill_tool_binding_skill` (`skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='技能工具关系表';

CREATE TABLE IF NOT EXISTS `tool_catalog` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工具目录主键',
                               `tool_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工具唯一名称',
                               `display_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '展示名称',
                               `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '工具描述',
                               `tool_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工具类型：GLOBAL/RUNTIME/SKILL_NATIVE/MCP_REMOTE/LOWCODE_API/DATASET_TOOL/KNOWLEDGE_BASE_TOOL',
                               `bindable` tinyint DEFAULT '0' COMMENT '是否允许被技能追加绑定，1=允许，0=不允许',
                               `enabled_global` tinyint NOT NULL DEFAULT '0' COMMENT '是否全局可用，1=全局注入对话，0=仅按需绑定/使用',
                               `owner_skill_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属运行时技能名称，公共工具为空',
                               `source` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '工具来源',
                               `sort_order` int DEFAULT '0' COMMENT '排序值，越小越靠前',
                               `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_tool_catalog_tool_name` (`tool_name`),
                               KEY `idx_tool_catalog_type_sort` (`tool_type`,`sort_order`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='工具业务目录表';

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'inventory', '库存管理', '查询服装商品的实时库存、尺码分布和缺货风险。', '库存管理', 'runtime', 1, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'inventory');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'pricing', '定价分析', '查询商品售价、成本和毛利空间，辅助定价决策。', '定价决策', 'runtime', 1, 10
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'pricing');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'trend', '销售趋势', '分析热销趋势、需求变化和未来销量预测。', '销售分析', 'runtime', 1, 20
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'trend');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'supplier', '供应商管理', '查询供应商目录、批发价格、库存和报价信息。', '采购供应', 'runtime', 1, 30
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'supplier');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'purchase', '采购策略', '基于库存与销售趋势生成补货和采购优化建议。', '采购供应', 'runtime', 1, 40
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'purchase');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'weather', '天气助手', '查询城市天气信息，辅助出行和经营判断。', '信息查询', 'runtime', 1, 50
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'weather');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'fashion-guide', '时尚导购', '提供服装行业趋势、搭配建议和参考资料。', '行业导购', 'runtime', 1, 60
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'fashion-guide');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'algorithmic-art', '算法艺术', '通过代码生成算法艺术与交互式视觉作品。', '创意生成', 'runtime', 1, 70
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'algorithmic-art');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'pdf-extractor', 'PDF 提取', '提取 PDF 文本和元数据，便于分析与处理。', '文档处理', 'runtime', 1, 80
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'pdf-extractor');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'pdf', 'PDF 文档处理', '读取、提取、合并、拆分和生成 PDF 文档，支持表单填写、加密与 OCR 场景。', '文档处理', 'runtime', 1, 90
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'pdf');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'docx', 'Word 文档处理', '创建、读取、编辑和重组 Word 文档，支持格式调整、内容替换和专业文档生成。', '文档处理', 'runtime', 1, 100
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'docx');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'pptx', 'PPT 生成与编辑', '读取、生成和编辑 PPT 演示文稿，支持模板改写、内容提取和页面检查。', '演示制作', 'runtime', 1, 110
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'pptx');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'xlsx', 'Excel 表格处理', '读取、整理、计算和生成 Excel 表格，支持公式、格式化和数据分析。', '数据处理', 'runtime', 1, 120
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'xlsx');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'ui-ux-pro-max', 'UI/UX 设计专家', '提供界面设计、交互优化和前端体验改进建议。', '设计体验', 'runtime', 1, 130
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'ui-ux-pro-max');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'market-research', '市场研究', '开展市场调研、竞品分析、投资人尽调和行业研究，输出带来源依据的决策建议。', '商业研究', 'runtime', 1, 140
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'market-research');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'contract-review-pro', '专业合同审核', '基于合同审核方法论提供合同审阅、风险识别和建议输出。', '法务合规', 'runtime', 1, 150
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'contract-review-pro');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'legal-consultation', '法律案件查询', '根据案件描述分析法律风险、识别相关法条，并提供处理建议、证据收集指导和立案条件说明。', '法务合规', 'runtime', 1, 160
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'legal-consultation');

INSERT INTO `skill_catalog` (`runtime_skill_name`, `display_name`, `description`, `category`, `source`, `visible`, `sort_order`)
SELECT 'form-app-assistant', '表单应用助手', '引导创建或扩展表单应用，结合文字、Excel 与参考资料生成标准化表单模型。', '表单搭建', 'runtime', 1, 170
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `skill_catalog` WHERE `runtime_skill_name` = 'form-app-assistant');

UPDATE `skill_catalog`
SET `icon` = CASE MOD(CRC32(`runtime_skill_name`), 6)
    WHEN 0 THEN 'grid_view'
    WHEN 1 THEN 'smart_toy'
    WHEN 2 THEN 'rocket_launch'
    WHEN 3 THEN 'inventory_2'
    WHEN 4 THEN 'dataset'
    ELSE 'hub'
END;

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'readFile', '读取文件', '读取本地 UTF-8 文本文件内容。', 'GLOBAL', 1, NULL, 'runtime', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'readFile');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'writeFile', '写入文件', '将 UTF-8 文本写入本地文件，必要时自动创建目录。', 'GLOBAL', 1, NULL, 'runtime', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'writeFile');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'runPython', '执行 Python', '执行指定 Python 脚本并返回标准输出。', 'GLOBAL', 1, NULL, 'runtime', 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'runPython');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'listActiveSkills', '查看已激活技能', '列出当前已激活技能及其说明，便于判断可调用能力。', 'GLOBAL', 1, NULL, 'runtime', 3
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'listActiveSkills');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'loadSkillContent', '读取技能内容', '读取指定技能的完整内容，用于查看技能说明和可用工具。', 'GLOBAL', 1, NULL, 'runtime', 4
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'loadSkillContent');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'loadSkillReference', '读取技能参考资料', '读取指定技能的参考资料条目，用于补充技能上下文。', 'GLOBAL', 1, NULL, 'runtime', 5
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'loadSkillReference');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'checkInventory', '查询库存', '查询指定品类或全部商品的库存、尺码和库存状态。', 'SKILL_NATIVE', 0, 'inventory', 'runtime', 100
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'checkInventory');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'getPricing', '查询定价', '查询商品售价、成本价、毛利率和建议价格区间。', 'SKILL_NATIVE', 0, 'pricing', 'runtime', 110
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'getPricing');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'getSalesTrends', '销售趋势分析', '查询指定周期内的热销商品、销售速度和市场趋势。', 'SKILL_NATIVE', 0, 'trend', 'runtime', 120
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'getSalesTrends');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'getPredictedDemand', '需求预测', '预测未来周期的需求量、建议备货量和风险等级。', 'SKILL_NATIVE', 0, 'trend', 'runtime', 121
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'getPredictedDemand');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'getSupplierCatalog', '供应商目录', '查询供应商可售商品、批发价、起订量和供货情况。', 'SKILL_NATIVE', 0, 'supplier', 'runtime', 130
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'getSupplierCatalog');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'getSupplierQuote', '获取供应商报价', '根据供应商 SKU 和数量生成明细报价与折扣信息。', 'SKILL_NATIVE', 0, 'supplier', 'runtime', 131
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'getSupplierQuote');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'generatePurchaseStrategy', '生成采购策略', '根据预算和优先级生成补货与采购策略建议。', 'SKILL_NATIVE', 0, 'purchase', 'runtime', 140
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'generatePurchaseStrategy');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'optimizePurchaseOrder', '优化采购单', '优化采购单数量与成本结构，提升 ROI 和折扣收益。', 'SKILL_NATIVE', 0, 'purchase', 'runtime', 141
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'optimizePurchaseOrder');

INSERT INTO `tool_catalog` (`tool_name`, `display_name`, `description`, `tool_type`, `bindable`, `owner_skill_name`, `source`, `sort_order`)
SELECT 'getWeather', '查询天气', '查询指定城市的天气、温度、湿度和风速。', 'SKILL_NATIVE', 0, 'weather', 'runtime', 150
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tool_catalog` WHERE `tool_name` = 'getWeather');

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`)
SELECT s.`id`, 'checkInventory', 'NATIVE'
FROM `skill_catalog` s
WHERE s.`runtime_skill_name` = 'inventory'
  AND NOT EXISTS (
      SELECT 1 FROM `skill_tool_binding` b
      WHERE b.`skill_id` = s.`id` AND b.`tool_name` = 'checkInventory'
  );

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`)
SELECT s.`id`, 'getPricing', 'NATIVE'
FROM `skill_catalog` s
WHERE s.`runtime_skill_name` = 'pricing'
  AND NOT EXISTS (
      SELECT 1 FROM `skill_tool_binding` b
      WHERE b.`skill_id` = s.`id` AND b.`tool_name` = 'getPricing'
  );

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`)
SELECT s.`id`, 'getSalesTrends', 'NATIVE'
FROM `skill_catalog` s
WHERE s.`runtime_skill_name` = 'trend'
  AND NOT EXISTS (
      SELECT 1 FROM `skill_tool_binding` b
      WHERE b.`skill_id` = s.`id` AND b.`tool_name` = 'getSalesTrends'
  );

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`)
SELECT s.`id`, 'getPredictedDemand', 'NATIVE'
FROM `skill_catalog` s
WHERE s.`runtime_skill_name` = 'trend'
  AND NOT EXISTS (
      SELECT 1 FROM `skill_tool_binding` b
      WHERE b.`skill_id` = s.`id` AND b.`tool_name` = 'getPredictedDemand'
  );

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`)
SELECT s.`id`, 'getSupplierCatalog', 'NATIVE'
FROM `skill_catalog` s
WHERE s.`runtime_skill_name` = 'supplier'
  AND NOT EXISTS (
      SELECT 1 FROM `skill_tool_binding` b
      WHERE b.`skill_id` = s.`id` AND b.`tool_name` = 'getSupplierCatalog'
  );

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`)
SELECT s.`id`, 'getSupplierQuote', 'NATIVE'
FROM `skill_catalog` s
WHERE s.`runtime_skill_name` = 'supplier'
  AND NOT EXISTS (
      SELECT 1 FROM `skill_tool_binding` b
      WHERE b.`skill_id` = s.`id` AND b.`tool_name` = 'getSupplierQuote'
  );

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`)
SELECT s.`id`, 'generatePurchaseStrategy', 'NATIVE'
FROM `skill_catalog` s
WHERE s.`runtime_skill_name` = 'purchase'
  AND NOT EXISTS (
      SELECT 1 FROM `skill_tool_binding` b
      WHERE b.`skill_id` = s.`id` AND b.`tool_name` = 'generatePurchaseStrategy'
  );

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`)
SELECT s.`id`, 'optimizePurchaseOrder', 'NATIVE'
FROM `skill_catalog` s
WHERE s.`runtime_skill_name` = 'purchase'
  AND NOT EXISTS (
      SELECT 1 FROM `skill_tool_binding` b
      WHERE b.`skill_id` = s.`id` AND b.`tool_name` = 'optimizePurchaseOrder'
  );

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`)
SELECT s.`id`, 'getWeather', 'NATIVE'
FROM `skill_catalog` s
WHERE s.`runtime_skill_name` = 'weather'
  AND NOT EXISTS (
      SELECT 1 FROM `skill_tool_binding` b
      WHERE b.`skill_id` = s.`id` AND b.`tool_name` = 'getWeather'
  );

CREATE TABLE IF NOT EXISTS `model_vendor` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模型厂商主键',
                               `vendor_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模型厂商编码',
                               `vendor_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模型厂商名称',
                               `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '厂商描述',
                               `default_base_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '厂商默认 Base URL',
                               `default_api_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '厂商默认 API Key',
                               `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DRAFT',
                               `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_model_vendor_code` (`vendor_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='模型厂商';

CREATE TABLE IF NOT EXISTS `model_definition` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模型主键',
                               `model_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模型编码',
                               `display_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模型展示名称',
                               `capability_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '能力类型：CHAT/EMBEDDING/RERANK',
                               `vendor_id` bigint NOT NULL COMMENT '模型厂商 ID',
                               `adapter_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '适配器类型：QWEN_ONLINE/VLLM',
                               `protocol` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '协议类型',
                               `base_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模型服务 Base URL',
                               `api_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模型密钥',
                               `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '接口路径',
                               `model_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '实际模型名',
                               `temperature` decimal(6,4) DEFAULT NULL COMMENT '对话采样温度',
                               `max_tokens` int DEFAULT NULL COMMENT '对话最大输出 token',
                               `system_prompt` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '模型默认系统提示词',
                               `enable_thinking` tinyint(1) DEFAULT NULL COMMENT '是否开启 thinking',
                               `dimensions` int DEFAULT NULL COMMENT '向量维度',
                               `timeout_ms` int DEFAULT NULL COMMENT '超时时间（毫秒）',
                               `fallback_rrf` tinyint(1) DEFAULT NULL COMMENT 'rerank 失败时是否回退 RRF',
                               `extra_config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '扩展配置 JSON',
                               `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DRAFT',
                               `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_model_definition_code` (`model_code`),
                               KEY `idx_model_definition_vendor` (`vendor_id`),
                               KEY `idx_model_definition_capability_status` (`capability_type`,`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='模型定义';

CREATE TABLE IF NOT EXISTS `model_default_binding` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '默认模型绑定主键',
                               `capability_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '能力类型：CHAT/EMBEDDING/RERANK',
                               `model_id` bigint NOT NULL COMMENT '默认模型 ID',
                               `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_model_default_binding_capability` (`capability_type`),
                               KEY `idx_model_default_binding_model` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='默认模型绑定';

INSERT INTO `model_vendor` (`vendor_code`, `vendor_name`, `description`, `default_base_url`, `default_api_key`, `status`)
SELECT 'QWEN_ONLINE', '通义千问', '预置模型模式，默认只需要配置厂商级 API Key 即可使用。', 'https://dashscope.aliyuncs.com/compatible-mode', '', 'ACTIVE'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `model_vendor` WHERE `vendor_code` = 'QWEN_ONLINE'
);

INSERT INTO `model_vendor` (`vendor_code`, `vendor_name`, `description`, `default_base_url`, `default_api_key`, `status`)
SELECT 'VLLM', 'vLLM', '自定义模型模式，适合离线部署与私有模型服务。', '', '', 'ACTIVE'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `model_vendor` WHERE `vendor_code` = 'VLLM'
);

INSERT INTO `model_definition` (
    `model_code`, `display_name`, `capability_type`, `vendor_id`, `adapter_type`, `protocol`,
    `base_url`, `api_key`, `path`, `model_name`, `extra_config_json`, `status`
)
SELECT 'qwen-chat-default', '通义对话模型', 'CHAT', v.`id`, 'QWEN_ONLINE', '', '', '', '/v1/chat/completions', 'qwen3-max', '', 'ACTIVE'
FROM `model_vendor` v
WHERE v.`vendor_code` = 'QWEN_ONLINE'
  AND NOT EXISTS (SELECT 1 FROM `model_definition` WHERE `model_code` = 'qwen-chat-default');

INSERT INTO `model_definition` (
    `model_code`, `display_name`, `capability_type`, `vendor_id`, `adapter_type`, `protocol`,
    `base_url`, `api_key`, `path`, `model_name`, `extra_config_json`, `status`
)
SELECT 'qwen-chat-plus', '通义增强对话模型', 'CHAT', v.`id`, 'QWEN_ONLINE', '', '', '', '/v1/chat/completions', 'qwen-plus', '', 'ACTIVE'
FROM `model_vendor` v
WHERE v.`vendor_code` = 'QWEN_ONLINE'
  AND NOT EXISTS (SELECT 1 FROM `model_definition` WHERE `model_code` = 'qwen-chat-plus');

INSERT INTO `model_definition` (
    `model_code`, `display_name`, `capability_type`, `vendor_id`, `adapter_type`, `protocol`,
    `base_url`, `api_key`, `path`, `model_name`, `extra_config_json`, `status`
)
SELECT 'qwen-chat-turbo', '通义极速对话模型', 'CHAT', v.`id`, 'QWEN_ONLINE', '', '', '', '/v1/chat/completions', 'qwen-turbo', '', 'ACTIVE'
FROM `model_vendor` v
WHERE v.`vendor_code` = 'QWEN_ONLINE'
  AND NOT EXISTS (SELECT 1 FROM `model_definition` WHERE `model_code` = 'qwen-chat-turbo');

INSERT INTO `model_definition` (
    `model_code`, `display_name`, `capability_type`, `vendor_id`, `adapter_type`, `protocol`,
    `base_url`, `api_key`, `path`, `model_name`, `extra_config_json`, `status`
)
SELECT 'qwen-chat-long', '通义长文本对话模型', 'CHAT', v.`id`, 'QWEN_ONLINE', '', '', '', '/v1/chat/completions', 'qwen-long', '', 'ACTIVE'
FROM `model_vendor` v
WHERE v.`vendor_code` = 'QWEN_ONLINE'
  AND NOT EXISTS (SELECT 1 FROM `model_definition` WHERE `model_code` = 'qwen-chat-long');

INSERT INTO `model_definition` (
    `model_code`, `display_name`, `capability_type`, `vendor_id`, `adapter_type`, `protocol`,
    `base_url`, `api_key`, `path`, `model_name`, `extra_config_json`, `status`
)
SELECT 'qwen-embedding-default', '通义向量模型', 'EMBEDDING', v.`id`, 'QWEN_ONLINE', '', '', '', '/v1/embeddings', 'text-embedding-v4', '', 'ACTIVE'
FROM `model_vendor` v
WHERE v.`vendor_code` = 'QWEN_ONLINE'
  AND NOT EXISTS (SELECT 1 FROM `model_definition` WHERE `model_code` = 'qwen-embedding-default');

INSERT INTO `model_definition` (
    `model_code`, `display_name`, `capability_type`, `vendor_id`, `adapter_type`, `protocol`,
    `base_url`, `api_key`, `path`, `model_name`, `extra_config_json`, `status`
)
SELECT 'qwen-rerank-default', '通义 Rerank 模型', 'RERANK', v.`id`, 'QWEN_ONLINE', 'dashscope',
       'https://dashscope.aliyuncs.com', '', '/api/v1/services/rerank/text-rerank/text-rerank', 'gte-rerank-v2', '', 'ACTIVE'
FROM `model_vendor` v
WHERE v.`vendor_code` = 'QWEN_ONLINE'
  AND NOT EXISTS (SELECT 1 FROM `model_definition` WHERE `model_code` = 'qwen-rerank-default');

INSERT INTO `model_default_binding` (`capability_type`, `model_id`)
SELECT 'CHAT', m.`id`
FROM `model_definition` m
WHERE m.`model_code` = 'qwen-chat-default'
  AND NOT EXISTS (SELECT 1 FROM `model_default_binding` WHERE `capability_type` = 'CHAT');

INSERT INTO `model_default_binding` (`capability_type`, `model_id`)
SELECT 'EMBEDDING', m.`id`
FROM `model_definition` m
WHERE m.`model_code` = 'qwen-embedding-default'
  AND NOT EXISTS (SELECT 1 FROM `model_default_binding` WHERE `capability_type` = 'EMBEDDING');

INSERT INTO `model_default_binding` (`capability_type`, `model_id`)
SELECT 'RERANK', m.`id`
FROM `model_definition` m
WHERE m.`model_code` = 'qwen-rerank-default'
  AND NOT EXISTS (SELECT 1 FROM `model_default_binding` WHERE `capability_type` = 'RERANK');

CREATE TABLE IF NOT EXISTS `mcp_server` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'MCP server 主键',
                               `server_key` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '稳定唯一标识，用于生成工具名前缀',
                               `display_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '展示名称',
                               `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '描述',
                               `server_scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'INTERNAL' COMMENT '服务范围：INTERNAL/EXTERNAL',
                               `transport_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '传输类型：STREAMABLE_HTTP/SSE',
                               `endpoint` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '远程 MCP endpoint 或 base URL',
                               `auth_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'NONE' COMMENT '鉴权类型：NONE/BEARER_TOKEN',
                               `auth_config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '鉴权配置 JSON',
                               `headers_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '自定义请求头 JSON',
                               `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用，1=启用，0=停用',
                               `enabled_global` tinyint NOT NULL DEFAULT '0' COMMENT '是否全局可用，1=全局，0=非全局',
                               `last_refresh_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'IDLE' COMMENT '最近刷新状态',
                               `last_refresh_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最近刷新摘要',
                               `last_refreshed_at` datetime DEFAULT NULL COMMENT '最近刷新时间',
                               `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_mcp_server_key` (`server_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='远程 MCP server 配置表';

CREATE TABLE IF NOT EXISTS `system_config` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置主键',
                               `config_key` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置唯一标识',
                               `config_value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '配置值，支持 JSON 或普通字符串',
                               `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=启用，0=停用',
                               `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_system_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='系统配置表';

INSERT INTO `system_config` (`config_key`, `config_value`, `status`)
SELECT 'token_quota_settings', '{"initialGrantTokens":1000000}', 0
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `system_config` WHERE `config_key` = 'token_quota_settings'
);

CREATE TABLE IF NOT EXISTS `lowcode_api_catalog` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '低代码 API 目录主键',
                               `platform_key` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '低代码平台标识',
                               `app_id` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '应用 ID',
                               `app_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '应用名称',
                               `api_id` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '远程 API ID',
                               `api_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '远程 API Code',
                               `api_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API 名称',
                               `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'API 描述',
                               `remote_schema_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '远程原始 schema JSON',
                               `tool_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '注册后的工具名称',
                               `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用，1=启用，0=停用',
                               `last_sync_at` datetime DEFAULT NULL COMMENT '最近同步时间',
                               `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_lowcode_api_catalog_platform_code` (`platform_key`,`api_code`),
                               UNIQUE KEY `uk_lowcode_api_catalog_tool_name` (`tool_name`),
                               KEY `idx_lowcode_api_catalog_platform_app` (`platform_key`,`app_id`,`id`),
                               KEY `idx_lowcode_api_catalog_enabled` (`enabled`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='低代码平台 API 目录表';

CREATE TABLE IF NOT EXISTS `skill_package_install` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '技能包安装主键',
                               `package_id` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '包唯一标识',
                               `runtime_skill_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '运行时技能名称',
                               `package_version` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '包版本',
                               `package_format_version` int DEFAULT '1' COMMENT '包格式版本',
                               `source_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '上传文件名',
                               `package_sha256` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '包 SHA-256',
                               `install_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '安装模式',
                               `install_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '安装状态',
                               `dependency_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '依赖安装状态',
                               `installed_by` bigint DEFAULT NULL COMMENT '安装人',
                               `installed_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '安装时间',
                               `summary_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '安装摘要 JSON',
                               PRIMARY KEY (`id`),
                               KEY `idx_skill_package_install_package` (`package_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='技能包安装记录表';

CREATE TABLE IF NOT EXISTS `skill_package_file` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '技能包文件主键',
                               `install_id` bigint NOT NULL COMMENT '所属安装记录 ID',
                               `package_id` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '包唯一标识',
                               `relative_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '相对技能目录路径',
                               `file_sha256` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件 SHA-256',
                               `file_size` bigint DEFAULT NULL COMMENT '文件大小',
                               `file_role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件角色',
                               `operation` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '本次安装操作',
                               `managed` tinyint DEFAULT '1' COMMENT '是否受管',
                               `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               PRIMARY KEY (`id`),
                               KEY `idx_skill_package_file_install` (`install_id`),
                               KEY `idx_skill_package_file_package` (`package_id`,`relative_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='技能包受管文件记录表';

CREATE TABLE IF NOT EXISTS `skill_studio_project` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '技能工坊项目主键',
  `project_code` char(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '项目编码',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '项目名称',
  `description` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '项目需求描述',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '项目状态',
  `project_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'SKILL' COMMENT '项目类型',
  `draft_skill_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联草稿 skill 名称',
  `runtime_skill_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '运行时技能名',
  `icon` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Material icon 名称',
  `icon_color` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图标主题色',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '技能分类',
  `draft_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '草稿目录路径',
  `cover_summary` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列表摘要',
  `initial_prompt` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '创建项目时的初始描述',
  `project_hints_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '项目级生成提示 JSON',
  `project_constraints_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '项目级生成约束 JSON',
  `tool_bindings_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '项目工具绑定配置 JSON',
  `tool_settings_digest` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '工具设置摘要',
  `last_generated_tool_digest` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '最近一次成功生成使用的工具设置摘要',
  `last_session_id` bigint DEFAULT NULL COMMENT '最近会话ID',
  `last_message_preview` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最近消息摘要',
  `create_user_id` bigint NOT NULL COMMENT '创建用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_studio_project_code` (`project_code`),
  KEY `idx_skill_studio_project_user_updated` (`create_user_id`, `updated_at`),
  KEY `idx_skill_studio_project_user_status` (`create_user_id`, `status`),
  KEY `idx_skill_studio_project_archived` (`archived_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='技能工坊项目表';
