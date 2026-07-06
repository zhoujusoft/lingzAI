SET @message_type_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'conversation_message'
    AND COLUMN_NAME = 'message_type'
);

SET @message_type_ddl := IF(
  @message_type_exists = 0,
  'ALTER TABLE `conversation_message` ADD COLUMN `message_type` varchar(32) DEFAULT ''normal'' COMMENT ''消息类型：normal=普通消息，event=事件消息''',
  'SELECT 1'
);

PREPARE message_type_stmt FROM @message_type_ddl;
EXECUTE message_type_stmt;
DEALLOCATE PREPARE message_type_stmt;
