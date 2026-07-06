ALTER TABLE `conversation_message`
    ADD COLUMN `segments_json` json DEFAULT NULL COMMENT '消息时间线片段JSON' AFTER `content`;
