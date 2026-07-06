-- 统一工具库全局可用开关

ALTER TABLE `tool_catalog`
    ADD COLUMN `enabled_global` tinyint NOT NULL DEFAULT '0' COMMENT '是否全局可用，1=全局注入对话，0=仅按需绑定/使用' AFTER `bindable`;



-- 用户 token 额度账户与配置

CREATE TABLE IF NOT EXISTS `user_token_account` (
                                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                                    `user_id` bigint NOT NULL COMMENT '用户 ID',
                                                    `granted_tokens` bigint NOT NULL DEFAULT '0' COMMENT '累计发放 token',
                                                    `consumed_tokens` bigint NOT NULL DEFAULT '0' COMMENT '累计消耗 token',
                                                    `remaining_tokens` bigint NOT NULL DEFAULT '0' COMMENT '剩余 token',
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

INSERT INTO `system_config` (`config_key`, `config_value`, `status`)
SELECT 'token_quota_settings', '{"initialGrantTokens":1000000}', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_config` WHERE `config_key` = 'token_quota_settings'
);


ALTER TABLE `user_token_account`
    ADD COLUMN `is_unlimited` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否无限制' AFTER `remaining_tokens`;

