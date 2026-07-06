ALTER TABLE `user_agent`
    ADD COLUMN `skill_preference_configured` tinyint NOT NULL DEFAULT '0'
        COMMENT '是否已配置个人技能启用偏好，1=已配置，0=未配置默认全启用'
        AFTER `avatar_object_name`;
