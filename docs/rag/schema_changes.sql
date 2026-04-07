-- RAG 文档切片入库功能 - 数据库变更脚本
-- 执行时间：2026-03-06
-- 影响表：knowledge_document, document_chunk

-- ============================================
-- 1. knowledge_document 表变更
-- ============================================

-- 1.1 修改 status 字段类型为 INT (从 String 改为 Integer)
ALTER TABLE knowledge_document 
MODIFY COLUMN status INT DEFAULT 0 COMMENT '文档状态：0-待处理 1-处理中 2-已处理 3-失败';

-- 1.2 新增 error_message 字段
ALTER TABLE knowledge_document 
ADD COLUMN error_message TEXT COMMENT '失败原因' AFTER status;

-- 1.3 新增 chunk_strategy 字段
ALTER TABLE knowledge_document 
ADD COLUMN chunk_strategy VARCHAR(20) DEFAULT 'AUTO' COMMENT '切片策略：AUTO/DELIMITER_WINDOW/HEADING_DIRECTORY' AFTER status;

-- 1.4 新增 chunk_config 字段 (JSON 类型)
ALTER TABLE knowledge_document 
ADD COLUMN chunk_config JSON COMMENT '切片配置' AFTER chunk_strategy;

-- ============================================
-- 2. document_chunk 表变更
-- ============================================

-- 2.1 新增 headings 字段 (JSON 数组)
ALTER TABLE document_chunk 
ADD COLUMN headings JSON COMMENT '标题层级：["第 1 章", "1.1 节"]' AFTER chunk_content;

-- 2.2 新增 chunk_type 字段
ALTER TABLE document_chunk 
ADD COLUMN chunk_type VARCHAR(50) DEFAULT 'WINDOW' COMMENT '分块类型：HEADING_LEVEL_1/HEADING_LEVEL_2/HEADING_LEVEL_3/PARAGRAPH/WINDOW' AFTER headings;

-- 2.3 新增 embedding 字段 (预留向量化)
ALTER TABLE document_chunk 
ADD COLUMN embedding VECTOR(1024) COMMENT '向量 embedding' AFTER chunk_type;

-- ============================================
-- 3. 验证变更
-- ============================================

-- 查看 knowledge_document 表结构
DESC knowledge_document;

-- 查看 document_chunk 表结构
DESC document_chunk;
