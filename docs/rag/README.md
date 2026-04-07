# RAG 文档切片入库设计文档

**版本**: v1.0  
**最后更新**: 2026-03-06  
**状态**: 开发中  
**评审人**: 谢宏波、小灵  
**开发**: backend-dev

---

## 文档目录

- [功能设计](./rag-chunking-function-design.md) - 完整功能设计文档
- [API 设计](./rag-chunking-api-design.md) - 接口设计详情
- [数据库设计](./rag-chunking-database-design.md) - 数据模型变更
- [实现方案](./rag-chunking-implementation.md) - 核心算法实现

---

## 需求概述

实现完整的 RAG 文档切片入库链路：
```
文档上传 → MinIO 存储 → 异步解析 → 切片处理 → 批量入库 → 进度查询
```

### 核心功能

1. **文件上传**: MinIO 存储文档文件
2. **状态机**: 0 待处理 → 1 处理中 → 2 已处理 → 3 失败
3. **进度管理**: Redis 缓存，前端轮询
4. **切片策略**: 
   - `DELIMITER_WINDOW`: 分隔符窗口 (PDF/TXT/MD)
   - `HEADING_DIRECTORY`: Word 标题目录 (DOCX)
5. **重试机制**: 失败文档可重新解析

---

## 快速开始

### 前端上传示例

```javascript
POST /datasets/document
Content-Type: multipart/form-data

FormData:
- kbId: 13
- file: test.pdf
- chunkStrategy: AUTO
- chunkConfig: {"separator":"\\n\\n","maxLength":500,"overlapLength":50}
```

### 进度轮询示例

```javascript
GET /datasets/document/getProgress/1001

// 每 2 秒轮询，直到 status != 1
```

---

## 相关文档

- [AI 团队协作规范](../../ai-team-guides/airule.md)
- [通用 RAG 检索设计](./rag-retrieval-design.md)
- [提示词模板](../../ai-team-guides/prompt-templates.md)

---

**最后更新**: 2026-03-06
