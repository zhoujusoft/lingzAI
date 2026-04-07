# RAG 文档切片入库 - 功能设计文档

**版本**: v1.0  
**创建时间**: 2026-03-06  
**评审人**: 谢宏波、小灵  
**开发**: backend-dev  
**状态**: 开发中

---

## 1. 需求概述

实现完整的 RAG 文档切片入库链路：文档上传 → MinIO 存储 → 异步解析 → 切片处理 → 批量入库 → 进度查询。

### 1.1 核心功能

| 功能 | 说明 | 优先级 |
|------|------|--------|
| 文件上传 | MinIO 存储文档文件 | P0 |
| 状态机 | 0 待处理 → 1 处理中 → 2 已处理 → 3 失败 | P0 |
| 进度管理 | Redis 缓存进度，前端轮询查询 | P0 |
| 切片策略 | DELIMITER_WINDOW / HEADING_DIRECTORY | P0 |
| 重试机制 | 失败文档可重新解析 | P2 |

---

## 2. 数据模型变更

### 2.1 `knowledge_document` 表

```sql
-- 1. 修改 status 字段类型为 INT
ALTER TABLE knowledge_document 
MODIFY COLUMN status INT DEFAULT 0 COMMENT '文档状态：0-待处理 1-处理中 2-已处理 3-失败';

-- 2. 新增 error_message 字段
ALTER TABLE knowledge_document 
ADD COLUMN error_message TEXT COMMENT '失败原因' AFTER status;

-- 3. 新增 chunk_strategy 字段
ALTER TABLE knowledge_document 
ADD COLUMN chunk_strategy VARCHAR(20) DEFAULT 'AUTO' COMMENT '切片策略：AUTO/DELIMITER_WINDOW/HEADING_DIRECTORY' AFTER status;

-- 4. 新增 chunk_config 字段
ALTER TABLE knowledge_document 
ADD COLUMN chunk_config JSON COMMENT '切片配置' AFTER chunk_strategy;
```

### 2.2 `document_chunk` 表

```sql
-- 1. 新增 headings 字段
ALTER TABLE document_chunk 
ADD COLUMN headings JSON COMMENT '标题层级：["第 1 章", "1.1 节"]' AFTER chunk_content;

-- 2. 新增 chunk_type 字段
ALTER TABLE document_chunk 
ADD COLUMN chunk_type VARCHAR(50) DEFAULT 'WINDOW' COMMENT '分块类型：HEADING_LEVEL_1/PARAGRAPH/WINDOW' AFTER headings;

-- 3. 新增 embedding 字段 (预留)
ALTER TABLE document_chunk 
ADD COLUMN embedding VECTOR(1024) COMMENT '向量 embedding' AFTER chunk_type;
```

---

## 3. 接口设计

### 3.1 创建文档 (改造)

```http
POST /datasets/document
Content-Type: multipart/form-data

Parameters:
- kbId: Long (必填)
- file: MultipartFile (必填)
- chunkStrategy: String (可选，默认 AUTO)
- chunkConfig: String (可选，JSON 格式)

Response:
{
  "docId": 1001,
  "status": 0,
  "message": "文档已创建，开始后台处理"
}
```

### 3.2 进度查询 (完善)

```http
GET /datasets/document/getProgress/{docId}

Response (处理中):
{
  "docId": 1001,
  "status": 1,
  "progress": 65,
  "stage": "CHUNKING",
  "message": "正在分块",
  "updatedAt": "2026-03-06 17:30:00"
}
```

### 3.3 重试解析 (新增)

```http
POST /datasets/document/{docId}/retry

Response:
{
  "docId": 1001,
  "status": 0,
  "message": "已重新加入解析队列"
}
```

---

## 4. 核心流程

### 4.1 文档上传与解析

```
1. 前端上传 → POST /datasets/document
2. 后端接收 → 上传 MinIO → 返回 fileId
3. 创建文档记录 (status=0)
4. 触发 Quartz 任务 → runParseDocument(docId)
5. DocumentParseTask.parseDocument(docId):
   - status=1 (处理中)
   - Redis 进度：progress=0, stage=PARSING
   - 从 MinIO 读取文件
   - 执行切片
   - 批量入库
   - status=2/3 (成功/失败)
6. 前端轮询进度 (每 2 秒)
```

### 4.2 状态机流转

```
新建文档 → 0 (待处理)
       ↓
   触发任务 → 1 (处理中)
       ↓
   ┌──────┴──────┐
   ↓             ↓
 成功 → 2      失败 → 3 (可重试)
```

---

## 5. 切片策略

### 5.1 策略选择逻辑

| 文件类型 | 策略 | 说明 |
|----------|------|------|
| **DOCX** | HEADING_DIRECTORY | Word 标题目录模式 |
| **PDF** | DELIMITER_WINDOW | 分隔符窗口模式 |
| **TXT** | DELIMITER_WINDOW | 分隔符窗口模式 |
| **MD** | DELIMITER_WINDOW | 分隔符窗口模式 |

### 5.2 DELIMITER_WINDOW (分隔符窗口)

**配置参数**:
```json
{
  "separator": "\\n\\n",
  "maxLength": 500,
  "overlapLength": 50,
  "normalizeWhitespace": true,
  "removeUrlEmail": false
}
```

**算法**:
1. 读取文件内容为纯文本
2. 文本预处理 (替换空格、删除 URL)
3. 用分隔符分割 → 段落列表
4. 合并段落直到 maxLength
5. 添加 overlapLength 重叠
6. 生成 DocumentBlock 列表

### 5.3 HEADING_DIRECTORY (Word 标题)

**依赖**: Apache POI 5.x

**算法**:
1. 使用 Apache POI 读取 Word
2. 遍历段落，识别标题样式
3. 提取标题层级 (Heading 1/2/3)
4. 按标题分割文档结构
5. 每个标题下的正文作为 chunk
6. 记录完整标题路径 (headings 数组)

---

## 6. Redis 进度管理

### 6.1 Key 设计

```
Key: rag:progress:{docId}
Type: String (JSON)
TTL: 24 小时
```

### 6.2 数据结构

```json
{
  "docId": 1001,
  "status": 1,
  "progress": 65,
  "stage": "CHUNKING",
  "message": "正在分块",
  "updatedAt": "2026-03-06T17:30:00+08:00"
}
```

### 6.3 进度阶段

| 阶段 | 进度范围 | 说明 |
|------|----------|------|
| PARSING | 0-30% | 文档解析 |
| CHUNKING | 30-70% | 分块处理 |
| EMBEDDING | 70-90% | 向量化 |
| INDEXING | 90-100% | 索引入库 |
| COMPLETED | 100% | 完成 |
| FAILED | 0% | 失败 |

---

## 7. MinIO 文件存储

### 7.1 Bucket 设计

```
Bucket: documents
Key 格式：documents/{kbId}/{docId}/{uuid}.{ext}
```

### 7.2 上传流程

```
前端上传 → 后端接收 → MinIO 上传 → 返回 fileId
```

---

## 8. 异常处理

| 异常类型 | 处理 |
|----------|------|
| 文件为空 | 400 错误 |
| 格式不支持 | 400 错误 |
| MinIO 失败 | 500 错误 |
| 解析失败 | status=3, error_message |
| 切片失败 | status=3, error_message |

---

## 9. 测试方案

### 9.1 单元测试

- [ ] parseDocument() 正常流程
- [ ] parseDocument() 异常流程
- [ ] chunkByWindow() 分隔符切片
- [ ] chunkByHeading() Word 标题切片
- [ ] ProgressManager 进度更新
- [ ] MinioService 文件上传

### 9.2 集成测试

- [ ] PDF 上传 → 分隔符切片 → 入库
- [ ] Word 上传 → 标题切片 → 入库
- [ ] 失败文档 → 重试成功

---

## 10. 验收标准

- [ ] PDF/TXT/MD 使用分隔符切片，入库成功
- [ ] Word 按标题切片，入库成功
- [ ] 前端可轮询实时进度
- [ ] 失败文档可重试
- [ ] 状态机流转正确 (0→1→2/3)
- [ ] Redis 进度 24 小时自动过期

---

## 11. 依赖库

```xml
<!-- Apache POI -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>

<!-- MinIO -->
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.7</version>
</dependency>

<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

---

**文档结束**
