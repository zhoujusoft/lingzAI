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
