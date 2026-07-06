ALTER TABLE `conversation_session`
    ADD COLUMN `chat_model_id` bigint DEFAULT NULL COMMENT '会话绑定的对话模型ID' AFTER `scope_id`;
