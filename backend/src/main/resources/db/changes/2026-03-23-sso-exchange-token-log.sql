-- 任务：03-23-brainstorm-clarify-current-development-goal
-- 说明：新增外部系统换取当前系统 token 的审计日志表

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部系统换取当前系统token日志';
