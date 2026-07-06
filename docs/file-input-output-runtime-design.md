# 文件输入输出 Runtime 设计

**版本**: v1.0  
**日期**: 2026-06-01  
**状态**: 设计草案  
**适用范围**: 聊天、技能、渠道接入中的文件上传、文件解析、模型问答、文件产物下载

---

## 1. 目标

本方案先不考虑图片、OCR、文生图等多媒体能力，只设计文件输入和文件输出的基础闭环。

目标是让系统支持：

1. 用户上传文件后，文件进入系统 MinIO。
2. 系统登记文件资产，后续请求只通过 `fileIds` 引用文件。
3. Runtime 能读取和解析文件内容。
4. 大模型能结合用户问题和文件内容生成正确答案。
5. Runtime 生成的输出文件能被登记为产物。
6. 用户能在前端或渠道中下载输出文件。

一句话总结：

> 文件输入输出都围绕 `runtime_file_asset + MinIO` 这条资产链路完成，模型不直接接收二进制文件，而是通过 `fileIds -> parse_file / run_python -> answer / write_artifact` 完成文件理解和产物生成。

---

## 2. 核心原则

### 2.1 文件是系统资产

上传文件、运行时生成文件、最终产物都应进入统一文件资产模型。

核心资产表：

- `runtime_file_asset`

核心存储：

- MinIO

核心标识：

- `fileCode`
- `objectName`
- `assetCode`

### 2.2 请求只传文件引用

聊天请求不直接传文件内容，只传 `fileIds`。

示例：

```json
{
  "message": "帮我总结这个文件中的关键条款",
  "fileIds": ["01HYEXAMPLEFILECODE"]
}
```

### 2.3 Runtime 统一处理文件理解

Controller 和渠道适配器只负责上传、绑定和传递文件引用，不直接解析文件内容。

文件解析应发生在 Runtime 能力层：

- `parse_file`
- `file_read`
- `run_python`
- 专用技能工具

### 2.4 输出文件必须走 artifact

模型或工具生成的最终文件不能只停留在临时路径中。

所有可下载文件必须通过：

```text
write_artifact -> MinIO -> runtime_file_asset -> downloadUrl
```

---

## 3. 现有可复用能力

当前项目已经具备以下基础能力：

| 能力 | 现有位置 | 说明 |
| --- | --- | --- |
| 文件上传入口 | `ChatFileController` | `/files/upload` |
| 文件上传服务 | `ChatFileService` | 校验文件、上传 MinIO、返回 fileId |
| 文件资产登记 | `RuntimeFileAssetService` | 写入 `runtime_file_asset` |
| 文件资产模型 | `RuntimeFileAsset` | 保存文件元数据、MinIO objectName、会话绑定 |
| 文件解析工具 | `FileParseToolProvider` | 暴露 `parse_file` 给 Runtime |
| 文件解析服务 | `FileParseService` | 根据上传文件或 runtime 路径选择解析器 |
| 运行时产物输出 | `RuntimeArtifactService` | `write_artifact` 上传产物并返回下载信息 |
| 产物下载 | `ChatFileController` | `/files/artifacts/{artifactId}/download` |

需要补强的关键点：

> `ChatFileService.resolveFiles(fileIds)` 当前主要依赖内存 `uploadedFiles`。正式方案中，`fileIds` 必须以 `runtime_file_asset` 数据库记录为事实源，内存只能作为缓存。

---

## 4. 文件输入链路

### 4.1 上传流程

用户在前端或渠道上传文件后，后端执行以下步骤：

1. 校验文件是否为空。
2. 校验文件后缀和大小。
3. 拒绝脚本和可执行文件。
4. 上传文件到 MinIO。
5. 写入 `runtime_file_asset`。
6. 返回 `fileCode`、文件名、大小和下载描述信息。

推荐资产字段：

| 字段 | 示例 | 说明 |
| --- | --- | --- |
| `fileCode` | `01HY...` | 对外引用 ID |
| `fileRole` | `UPLOAD` | 用户上传文件 |
| `producerType` | `USER_UPLOAD` | 文件来源 |
| `userId` | `10001` | 文件所属用户 |
| `sessionCode` | `chat_xxx` | 可选，会话绑定 |
| `displayName` | `合同.docx` | 原始展示名 |
| `contentType` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | 内容类型 |
| `sizeBytes` | `102400` | 文件大小 |
| `sha256` | `...` | 内容哈希 |
| `bucket` | `documents` | MinIO bucket |
| `objectName` | `chat-files/10001/01HY...docx` | MinIO 对象名 |
| `status` | `ACTIVE` | 文件状态 |

### 4.2 聊天请求绑定文件

上传完成后，前端发起聊天请求时带上 `fileIds`：

```json
{
  "sessionId": "chat_001",
  "message": "请根据这个文件回答：付款条件是什么？",
  "fileIds": ["01HYEXAMPLEFILECODE"],
  "messageType": "normal"
}
```

后端进入统一 Runtime 请求语义：

- `message`
- `fileIds`
- `sessionId`
- `messageType`
- `eventPayload`
- `systemPromptAppend`
- `options`

### 4.3 Runtime 文件上下文组装

Runtime 准备请求时，需要完成：

1. 根据 `fileIds` 查询 `runtime_file_asset`。
2. 校验当前用户是否有权访问文件。
3. 生成 `fileListJson`。
4. 映射为逻辑路径：

```text
/uploads/合同.docx
/uploads/订单明细.xlsx
```

5. 将文件列表加入模型上下文。

提示模型：

- `.txt/.md/.csv/.json` 等文本文件可直接读取。
- `.docx/.pdf/.xlsx/.xls` 等二进制文件优先使用 `parse_file`。
- 表格统计、批量处理、复杂转换可使用 `run_python` 读取原始文件。

### 4.4 渠道文件上下文

微信、企业微信、钉钉等 IM 渠道经常无法在同一条消息里同时发送“文件 + 文本”。文件通常作为一条独立消息发送，用户后续可能立刻提问，也可能隔一段时间才提问，也可能完全不按预期补充说明。

因此，渠道文件能力不能依赖“上一条/下一条文本消息一定连贯”。更稳的设计是：

> 渠道收到文件后，立即把文件登记为当前会话的文件上下文资产；后续每次文本请求进入 Runtime 前，由系统自动判断是否需要带入这些文件。

#### 4.4.1 文件消息到达后的处理

当微信渠道收到文件消息时：

1. 下载文件二进制。
2. 上传到 MinIO。
3. 写入 `runtime_file_asset`，`fileRole = UPLOAD`。
4. 绑定到当前渠道会话：
   - `channelId`
   - `externalSessionKey`
   - `sessionCode`
   - `ownerUserId`
5. 写入或更新会话文件上下文。
6. 回复用户一个轻量确认：

```text
已收到文件《销售明细.xlsx》。
```

如果系统能识别文件类型，可以补充一句能力提示：

```text
这是一个表格文件，后续你可以直接问我统计、筛选或生成报告。
```

注意：

- 不要强制要求用户“请继续说明问题”。
- 不要在只有文件、没有问题时立刻让模型深度分析。
- 不要把文件消息直接转成“用户发送了一个文件”并立即进入 Runtime。

#### 4.4.2 会话文件上下文模型

建议在渠道侧维护会话级文件上下文。

可以新增独立表，也可以先复用 `runtime_file_asset` 加查询规则实现。

逻辑模型：

```java
ChannelSessionFileContext {
    Long channelId;
    String externalSessionKey;
    String sessionCode;
    Long ownerUserId;
    List<FileAssetRef> recentFiles;
}
```

`FileAssetRef` 建议包含：

```java
FileAssetRef {
    String fileCode;
    String fileName;
    String contentType;
    Long sizeBytes;
    LocalDateTime uploadedAt;
    String summary;
    String parseStatus;
    Boolean active;
}
```

最小实现可以不新建表，直接按以下条件查询最近文件：

```text
runtime_file_asset.user_id = change-me-user
runtime_file_asset.session_code = 当前 channel sessionCode
runtime_file_asset.file_role = UPLOAD
runtime_file_asset.status = ACTIVE
runtime_file_asset.created_at >= now - contextWindow
```

推荐 `contextWindow`：

- 默认 30 分钟。
- 业务文件密集场景可延长到 2 小时。
- 用户明确切换主题或清空文件上下文时结束。

#### 4.4.3 文本请求前的文件决策

每次渠道文本消息进入 Runtime 前，执行一次文件上下文决策。

推荐模型：

```java
FileContextDecision {
    boolean attachFiles;
    List<String> fileIds;
    String reason;
    boolean needClarification;
    List<String> clarificationCandidates;
}
```

自动带入文件的典型条件：

1. 当前会话最近只有一个文件，且用户问题较短。
2. 用户问题包含文件指代：
   - “这个文件”
   - “刚才的文件”
   - “附件”
   - “这张表”
   - “这个合同”
   - “这个 PDF”
3. 用户问题包含文件处理意图：
   - “总结”
   - “分析”
   - “提取”
   - “统计”
   - “筛选”
   - “生成报告”
   - “帮我看看”
4. 上一轮系统刚确认收到文件。
5. 最近对话主题一直围绕某个文件。

不带入文件的典型条件：

1. 用户问题明显与文件无关。
2. 用户明确切换主题。
3. 文件上下文已过期。
4. 文件数量很多，且无法从问题中判断目标文件。

需要澄清的典型条件：

1. 最近有多个文件。
2. 用户只说“分析一下”，但无法判断分析哪个文件。
3. 文件名、类型、上传时间都无法匹配问题。

澄清话术示例：

```text
我看到了多个文件：合同.docx、报价单.xlsx。你想分析哪一个？
```

#### 4.4.4 Runtime 请求组装

如果文件上下文决策结果为自动带入文件，则渠道文本请求在进入 Runtime 前补齐 `fileIds`。

用户实际输入：

```text
帮我统计销售额最高的客户
```

系统内部请求：

```json
{
  "message": "帮我统计销售额最高的客户",
  "fileIds": ["01HYSALESFILECODE"],
  "options": {
    "channelFileContext": {
      "autoAttached": true,
      "reason": "recent_single_file_with_file_analysis_intent"
    }
  }
}
```

这样用户不需要遵守固定消息顺序，文件仍能被自然地带入问答。

#### 4.4.5 聚合窗口只作为体验优化

可以保留一个很短的消息聚合窗口，但它不能成为核心机制。

推荐窗口：

- 普通连续短文本：`500ms - 1000ms`
- 文件和紧邻文本：`1s - 3s`

超过窗口后：

- 文件进入会话文件上下文。
- 后续文本由 `FileContextDecision` 自动判断是否关联文件。

也就是说：

> 聚合窗口解决“刚好连续发送”的体验问题；会话文件上下文解决“不知道用户什么时候、以什么方式提问”的稳定性问题。

---

## 5. 文件读取与解析链路

### 5.1 解析策略

文件解析不是上传时必须完成的动作，推荐采用“按需解析”。

模型需要理解文件内容时，再调用：

```text
parse_file(path, mode)
```

常见解析模式：

| 模式 | 适用场景 | 输出 |
| --- | --- | --- |
| `text` | Word、PDF 正文提取、合同条款问答 | 文本内容 |
| `structured` | Excel、CSV、ZIP、表结构分析 | sheet、列名、样例、结构摘要 |
| `markdown` | 文档转 markdown、保留标题层级 | markdown 文本 |

### 5.2 不同文件类型处理

| 文件类型 | 建议处理方式 |
| --- | --- |
| `.txt/.md` | 直接读取 UTF-8 文本 |
| `.json` | 结构化读取，必要时压缩展示 |
| `.csv` | 小文件可文本读取，大文件使用结构化解析或 Python |
| `.docx` | 提取段落、标题、表格文本 |
| `.pdf` | 提取可读文本，扫描件后续再接 OCR |
| `.xlsx/.xls` | 返回 sheet、列名、行数、样例行；复杂统计走 Python |
| `.zip` | 先解析目录结构，再按需解析内部文件 |

### 5.3 大文件策略

大文件不能一次性塞入 prompt。

推荐策略：

1. 先返回结构摘要。
2. 根据用户问题筛选相关 sheet、列、章节或页码。
3. 对相关片段做局部解析。
4. 必要时使用 Python 直接处理原始文件。

### 5.4 解析缓存

建议新增解析缓存，避免同一文件重复解析。

可新增表：

```text
file_parse_cache
```

建议字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `file_code` | 文件资产 ID |
| `sha256` | 文件内容哈希 |
| `mode` | `text` / `structured` / `markdown` |
| `status` | `SUCCESS` / `FAILED` |
| `content_json` | 解析结果 |
| `error_message` | 失败原因 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

缓存命中条件：

```text
fileCode + sha256 + mode
```

---

## 6. 模型问答链路

### 6.1 简单问答

适用于：

- 文档总结
- 条款问答
- 文件内容解释
- 小型表格说明

流程：

```text
用户问题 + fileIds
-> Runtime 查询文件资产
-> parse_file 提取内容
-> 模型基于解析内容回答
-> 返回文本答案
```

要求：

- 回答必须基于解析结果。
- 文件解析不足时要明确说明限制。
- 不允许凭空补全文件中不存在的内容。

### 6.2 表格计算与复杂处理

适用于：

- Excel 统计
- 多 sheet 汇总
- 批量转换
- 生成新文件
- 大文件筛选

流程：

```text
用户问题 + fileIds
-> Runtime 识别为执行型任务
-> 将文件物化到 /uploads
-> 生成最小 Python 脚本
-> run_python 处理文件
-> 输出结果或生成新文件
-> write_artifact 登记产物
```

### 6.3 答案可信度要求

模型回答中应尽量说明依据：

- 来源文件名
- sheet 名
- 章节标题
- 页码或行号，若解析器支持

示例：

```text
根据《采购合同.docx》中“付款方式”章节，付款条件为：合同签订后 5 个工作日内支付 30% 预付款，验收通过后支付剩余 70%。
```

---

## 7. 文件输出链路

### 7.1 输出类型

输出分两类：

1. 普通文本答案
2. 可下载文件产物

普通文本答案通过聊天 SSE `message` 返回。

文件产物必须通过 `write_artifact` 返回。

### 7.2 产物生成流程

当 Runtime 需要生成文件时：

1. 在 `/workspace` 或 `/outputs` 中生成文件。
2. 调用 `write_artifact`。
3. 后端上传文件到 MinIO。
4. 写入 `runtime_file_asset`。
5. SSE 返回 artifact 信息。

推荐 artifact 事件结构：

```json
{
  "artifact": {
    "id": "artifact-id",
    "fileName": "分析报告.docx",
    "path": "artifact://documents/runtime/artifacts/chat_001/xxx.docx",
    "downloadUrl": "/api/files/artifacts/{artifactId}/download?fileName=分析报告.docx",
    "previewUrl": "/api/files/artifacts/{artifactId}/preview?fileName=分析报告.docx",
    "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "previewable": false,
    "assetCode": "01HYOUTPUTFILECODE"
  }
}
```

### 7.3 输出资产字段

写入 `runtime_file_asset` 时建议字段：

| 字段 | 示例 | 说明 |
| --- | --- | --- |
| `fileCode` | `01HY...` | 输出资产 ID |
| `fileRole` | `ARTIFACT` | 最终产物 |
| `producerType` | `WRITE_ARTIFACT` | 由 Runtime 产物工具生成 |
| `userId` | `10001` | 所属用户 |
| `sessionCode` | `chat_001` | 所属会话 |
| `runId` | `20001` | 所属运行 |
| `originMessageId` | `30001` | 关联消息 |
| `displayName` | `分析报告.docx` | 展示名 |
| `logicalRoot` | `OUTPUTS` | 逻辑根 |
| `logicalPath` | `reports/分析报告.docx` | Runtime 逻辑路径 |
| `bucket` | `documents` | MinIO bucket |
| `objectName` | `runtime/artifacts/chat_001/xxx.docx` | MinIO 对象名 |
| `minioStatus` | `UPLOADED` | MinIO 状态 |

### 7.4 下载流程

前端展示文件卡片：

- 文件名
- 文件类型
- 文件大小
- 下载按钮
- 预览按钮，若 `previewable = true`

下载接口：

```text
GET /files/artifacts/{artifactId}/download?fileName=...
GET /files/assets/{fileCode}/download
```

---

## 8. 权限与安全

### 8.1 文件访问权限

所有文件读取、解析、下载都必须校验权限。

允许访问的条件：

1. 当前用户拥有该文件。
2. 文件属于当前会话，且当前用户拥有该会话。
3. 文件被明确标记为可公开访问。

### 8.2 文件类型限制

上传阶段必须限制：

- 禁止脚本文件。
- 禁止可执行文件。
- 限制最大文件大小。
- 限制允许后缀。

建议禁止：

```text
.py .sh .bat .cmd .ps1 .js .ts .jar .exe .msi
```

### 8.3 模型安全边界

模型不能直接执行用户上传的脚本。

如果用户上传脚本内容，只能作为需求或参考材料，由系统重新生成最小、可审计、面向当前任务的脚本。

### 8.4 临时路径安全

Runtime 只能访问受控逻辑路径：

```text
/uploads
/workspace
/outputs
/temp
```

路径解析必须经过 sandbox / path jail。

---

## 9. 推荐接口契约

### 9.1 上传文件

```text
POST /files/upload
Content-Type: multipart/form-data
```

请求字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `file` | 是 | 上传文件 |
| `sessionId` | 否 | 绑定会话 |
| `messageId` | 否 | 绑定消息 |
| `eventId` | 否 | 绑定事件 |

响应：

```json
{
  "id": "01HYFILECODE",
  "name": "合同.docx",
  "size": 102400,
  "error": null,
  "file": {
    "id": "01HYFILECODE",
    "fileName": "合同.docx",
    "bucket": "documents",
    "objectName": "chat-files/10001/01HYFILECODE.docx",
    "path": "chat-upload://chat-files/10001/01HYFILECODE.docx",
    "downloadUrl": "/api/files/artifacts/xxx/download?fileName=合同.docx",
    "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  }
}
```

### 9.2 发送聊天请求

```json
{
  "sessionId": "chat_001",
  "message": "请总结这个文件",
  "fileIds": ["01HYFILECODE"],
  "messageType": "normal"
}
```

### 9.3 下载文件资产

```text
GET /files/assets/{fileCode}/download
```

### 9.4 下载产物

```text
GET /files/artifacts/{artifactId}/download?fileName=...
```

---

## 10. 实施阶段

### P0：文件输入输出最小闭环

目标：

- 上传文件进入 MinIO。
- 文件资产写入 `runtime_file_asset`。
- 聊天请求传 `fileIds`。
- Runtime 能通过 `parse_file` 读取文件内容。
- 模型能结合文件内容回答问题。
- Runtime 能通过 `write_artifact` 生成可下载文件。

关键改动：

1. 将 `fileIds` 解析从内存缓存改为数据库查询。
2. 完善 `RuntimeFileAssetService` 的按用户、按 fileCode 查询能力。
3. 确保 `fileListJson` 可从数据库资产生成。
4. 确保 `/uploads/文件名` 能映射到 MinIO 文件。
5. 确保 `write_artifact` 返回的 `assetCode/downloadUrl` 可被前端展示。
6. 渠道文件消息到达后，先登记为会话文件上下文资产，不依赖用户紧接着补充文本。
7. 渠道文本消息进入 Runtime 前，执行 `FileContextDecision`，自动决定是否补齐最近文件的 `fileIds`。

### P1：文件解析增强

目标：

- 增加解析缓存。
- 优化 Word/PDF/Excel/CSV 解析效果。
- 对大文件做摘要和局部解析。
- 对渠道文件做轻量预处理，提升后续文件关联判断准确度。

关键改动：

1. 新增 `file_parse_cache`。
2. 增强 Excel 结构化摘要。
3. 增强 PDF 文本提取失败时的错误说明。
4. 支持按 sheet、列名、页码、章节进行局部解析。
5. 为渠道最近文件补充轻量元数据，例如文件类型、sheet 名、标题、前几段摘要。

### P2：复杂文件任务执行

目标：

- 表格统计、批量转换、报告生成等任务可自动进入代码执行。
- 输出文件统一登记为 artifact。

关键改动：

1. 强化任务意图判断。
2. 对 Excel/CSV 大文件优先使用 Python。
3. 将所有最终输出文件通过 `write_artifact` 发布。
4. 前端展示产物卡片和下载入口。

---

## 11. 最终链路图

```text
用户上传文件
  -> /files/upload
  -> ChatFileService
  -> MinIO
  -> runtime_file_asset(fileRole=UPLOAD)
  -> 返回 fileCode

渠道用户发送文件
  -> ChannelAdapter 下载文件
  -> MinIO
  -> runtime_file_asset(fileRole=UPLOAD)
  -> 绑定 channel session
  -> 写入会话文件上下文
  -> 回复轻量确认

用户提问 + fileIds
  -> ChatRuntimeRequestMapper
  -> ChatRuntimePreparedRequestAssembler
  -> 查询 runtime_file_asset
  -> 构造 fileListJson / /uploads 路径
  -> 模型根据问题调用 parse_file 或 run_python
  -> 生成文本答案或输出文件

渠道用户发送文本
  -> 查询会话文件上下文
  -> FileContextDecision 判断是否自动带入最近文件
  -> 组装 message + fileIds
  -> ChatRuntimeRequestMapper
  -> ChatRuntimePreparedRequestAssembler
  -> 模型根据问题调用 parse_file 或 run_python
  -> 生成文本答案或输出文件

输出文件
  -> write_artifact
  -> RuntimeArtifactService
  -> MinIO
  -> runtime_file_asset(fileRole=ARTIFACT)
  -> 返回 assetCode / downloadUrl / previewUrl
  -> 用户下载
```

---

## 12. 结论

文件能力的关键不是让模型直接处理二进制，而是建立稳定的文件资产链路。

推荐落地路径：

1. 所有输入文件统一进入 MinIO 和 `runtime_file_asset`。
2. 聊天请求统一传 `fileIds`。
3. Runtime 通过 `parse_file` 和 `run_python` 按需读取文件。
4. 模型基于解析证据回答，不凭空补全。
5. 所有输出文件统一通过 `write_artifact` 登记为可下载产物。

这样可以同时服务 Web 聊天、技能聊天、微信/IM 渠道和后续连接器能力，不需要为每个入口重复实现文件处理逻辑。
