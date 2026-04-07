-- 法律法规 RAG：为 document_chunk 增加分块元数据 JSON 字段
-- 执行方式：由开发人员手动在目标数据库执行

ALTER TABLE `document_chunk`
    ADD COLUMN `metadata_values` JSON NULL COMMENT '分块元数据，JSON格式，存放条号、章号等结构化信息' AFTER `chunk_type`;
