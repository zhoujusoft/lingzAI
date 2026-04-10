SET @message_type_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'imessages'
    AND COLUMN_NAME = 'message_type'
);

SET @message_type_ddl := IF(
  @message_type_exists = 0,
  'ALTER TABLE `imessages` ADD COLUMN `message_type` varchar(32) DEFAULT ''normal'' COMMENT ''消息类型：normal=普通消息，event=事件消息'' AFTER `query`',
  'SELECT 1'
);

PREPARE message_type_stmt FROM @message_type_ddl;
EXECUTE message_type_stmt;
DEALLOCATE PREPARE message_type_stmt;

UPDATE `imessages`
SET `message_type` = 'normal'
WHERE `message_type` IS NULL OR TRIM(`message_type`) = '';
