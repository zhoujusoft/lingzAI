-- IMPORTANT: keep this file encoded as UTF-8 (no BOM) to avoid mojibake on seed data.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `t_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `user_type` int DEFAULT NULL,
  `mobile` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `state` int DEFAULT NULL,
  `parent_iD` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_t_user_code` (`code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=Dynamic;

-- 初始管理员账号：admin，初始明文密码：admin123456（存储格式：32位md5 hex）
INSERT INTO `t_user` (`id`, `name`, `code`, `password`, `user_type`, `mobile`, `email`, `state`, `parent_iD`)
VALUES (1, '系统管理员', 'admin', 'a66abb5684c45962d887564f08346e8d', 0, '11111111111', NULL, 1, NULL)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `password` = VALUES(`password`),
  `state` = VALUES(`state`);

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
                                  `kb_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '知识库名称，唯一',
                                  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '知识库描述，可为空',
                                  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，自动记录',
                                  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，自动更新',
                                  PRIMARY KEY (`kb_id`)
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
                                      `status` enum('0','1','2','3') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '文档处理状态，仅对文档(is_folder=0)有效：0待处理、1处理中、2完成、3失败',
                                      `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '异步处理失败时的错误摘要',
                                      `file_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'MinIO对象ID，仅对文档(is_folder=0)有效',
                                      `chunk_strategy` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '切片策略，如AUTO、DELIMITER_WINDOW、HEADING_DIRECTORY',
                                      `chunk_config` json DEFAULT NULL COMMENT '切片参数配置，JSON格式',
                                      `document_json` json DEFAULT NULL COMMENT '文档解析JSON',
                                      PRIMARY KEY (`doc_id`)
) ENGINE=InnoDB AUTO_INCREMENT=498 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='存储文档和文件夹信息，支持目录层级和元数据';

-- chat_session ddl
CREATE TABLE `chat_session` (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话主键',
                                `session_code` char(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话编码（ULID）',
                                `session_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话类型：GENERAL_CHAT/SKILL_CHAT/KNOWLEDGE_QA',
                                `scope_id` bigint DEFAULT NULL COMMENT '会话作用域ID（知识问答场景可存 kb_id）',
                                `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '会话名称',
                                `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'normal' COMMENT '会话状态',
                                `last_message` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最近一条消息摘要',
                                `create_user_id` bigint NOT NULL COMMENT '创建用户ID',
                                `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (`id`),
                                UNIQUE KEY `uk_chat_session_code` (`session_code`),
                                KEY `idx_chat_session_user_type_updated` (`create_user_id`,`session_type`,`updated_at`,`id`),
                                KEY `idx_chat_session_scope` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户会话主表';

-- imessages ddl
CREATE TABLE `imessages` (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息主键',
                             `session_id` bigint NOT NULL COMMENT '关联会话ID',
                             `query` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '提问',
                             `question_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '问题类型',
                             `params_json` json DEFAULT NULL COMMENT '参数',
                             `answer` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '回答',
                             `ordinal` int DEFAULT NULL COMMENT '序号',
                             `document_list` json DEFAULT NULL COMMENT '文档信息',
                             `file_list` json DEFAULT NULL COMMENT '文件信息',
                             `consumes_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '问答耗时',
                             `error` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '错误信息',
                             `status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT '状态',
                             `final_query` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '最终提问',
                             `create_user_id` bigint DEFAULT NULL COMMENT '创建人ID',
                             `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             PRIMARY KEY (`id`),
                             KEY `idx_imessages_session_created` (`session_id`,`created_at`,`id`),
                             KEY `idx_imessages_user_created` (`create_user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='会话消息表';

CREATE TABLE IF NOT EXISTS `skill_catalog` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '技能目录主键',
                               `runtime_skill_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '运行时技能唯一名称',
                               `display_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '展示名称',
                               `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '展示描述',
                               `category` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '技能分类',
                               `source` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '技能来源',
                               `visible` tinyint DEFAULT '1' COMMENT '是否在前台技能市场可见，1=可见，0=隐藏',
                               `sort_order` int DEFAULT '0' COMMENT '排序值，越小越靠前',
                               `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_skill_catalog_runtime_skill` (`runtime_skill_name`),
                               KEY `idx_skill_catalog_visible_sort` (`visible`,`sort_order`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='技能业务目录表';

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
                               `tool_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工具类型：GLOBAL/SKILL_NATIVE/MCP_REMOTE',
                               `bindable` tinyint DEFAULT '0' COMMENT '是否允许被技能追加绑定，1=允许，0=不允许',
                               `owner_skill_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属运行时技能名称，公共工具为空',
                               `source` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '工具来源',
                               `sort_order` int DEFAULT '0' COMMENT '排序值，越小越靠前',
                               `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_tool_catalog_tool_name` (`tool_name`),
                               KEY `idx_tool_catalog_type_sort` (`tool_type`,`sort_order`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='工具业务目录表';

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
                               `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用，1=启用，0=停用',
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
