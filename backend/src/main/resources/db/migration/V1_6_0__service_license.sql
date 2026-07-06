-- 幂等方式添加 license_exempt 字段
SET @current_schema = DATABASE();

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @current_schema
              AND TABLE_NAME = 't_user'
              AND COLUMN_NAME = 'license_exempt'
        ),
        'SELECT 1',
        'ALTER TABLE `t_user` ADD COLUMN `license_exempt` tinyint(1) NOT NULL DEFAULT ''0'' COMMENT ''是否豁免 license 人数限制'' AFTER `role_id`'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 标记 admin 用户为豁免
UPDATE `t_user`
SET `license_exempt` = 1
WHERE LOWER(TRIM(`code`)) = 'admin';

-- 创建 license 相关表
CREATE TABLE IF NOT EXISTS `service_license` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `license_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'license 业务标识',
    `serial_no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '序列号',
    `revision` int NOT NULL DEFAULT '1' COMMENT '版本序号',
    `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '产品编码',
    `edition` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '版本类型',
    `customer_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户名称',
    `instance_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '实例码',
    `issued_at` datetime DEFAULT NULL COMMENT '签发时间',
    `effective_at` datetime DEFAULT NULL COMMENT '生效时间',
    `expires_at` datetime DEFAULT NULL COMMENT '过期时间',
    `max_active_users` int DEFAULT NULL COMMENT '最大启用用户数',
    `max_total_tokens` bigint DEFAULT NULL COMMENT '最大总 token 数',
    `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'license 状态',
    `feature_flags_json` json DEFAULT NULL COMMENT '功能开关 JSON',
    `raw_payload` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '原始 payload',
    `raw_signature` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '原始签名',
    `file_sha256` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'license 文件 sha256',
    `imported_by` bigint DEFAULT NULL COMMENT '导入人',
    `imported_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
    `last_verified_at` datetime DEFAULT NULL COMMENT '最近校验时间',
    `is_current` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否当前生效',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_service_license_license_id_revision` (`license_id`, `revision`),
    KEY `idx_service_license_current` (`is_current`, `id`),
    KEY `idx_service_license_status` (`status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='服务 license 主表';

CREATE TABLE IF NOT EXISTS `service_license_import_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `license_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'license 标识',
    `operator_user_id` bigint DEFAULT NULL COMMENT '操作人用户 ID',
    `import_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '导入状态',
    `failure_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '失败原因',
    `file_sha256` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件 sha256',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_service_license_import_log_created` (`created_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='服务 license 导入日志';

CREATE TABLE IF NOT EXISTS `service_license_token_account` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `license_id` bigint NOT NULL COMMENT 'license 表主键',
    `granted_tokens` bigint NOT NULL DEFAULT '0' COMMENT '授予 token',
    `consumed_tokens` bigint NOT NULL DEFAULT '0' COMMENT '已消耗 token',
    `reserved_tokens` bigint NOT NULL DEFAULT '0' COMMENT '预留 token',
    `remaining_tokens` bigint NOT NULL DEFAULT '0' COMMENT '剩余 token',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_service_license_token_account_license_id` (`license_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='服务 license token 账户';

CREATE TABLE IF NOT EXISTS `service_license_token_ledger` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `license_id` bigint NOT NULL COMMENT 'license 表主键',
    `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '来源类型',
    `source_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源标识',
    `user_id` bigint DEFAULT NULL COMMENT '用户 ID',
    `delta_tokens` bigint NOT NULL COMMENT '变更 token，可正可负',
    `balance_after` bigint NOT NULL COMMENT '变更后余额',
    `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_service_license_token_ledger_license_created` (`license_id`, `created_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='服务 license token 流水';
