# 附件处理与按需解析设计

**版本**: v1.0  
**日期**: 2026-04-28  
**状态**: 设计定稿  
**适用范围**: Lingz Agent Runtime 中聊天上传附件、运行时工作区 `uploads/`、附件解析能力与 skill 执行层的职责边界

---

## 1. 目标

本设计解决以下问题：

1. 上传附件后，系统到底应不应该立刻解析正文
2. 模型什么时候该看附件内容，什么时候只需要知道“有这个文件”
3. `file_read`、附件解析、`run_python` 之间的职责如何划分
4. 如何避免把 `.docx/.pdf/.xlsx` 这类二进制文件错误地当成文本读取
5. 如何兼容“模型理解内容”和“脚本直接处理原件”两类完全不同的场景

一句话目标：

> **附件首先是文件对象；只有在模型确实需要消费内容时，才触发解析。**

---

## 2. 结论先行

Lingz 的附件处理采用以下原则：

1. 上传后先保存文件对象与元数据
2. 会话运行时将附件物化到当前 session 的 `/uploads`
3. 不默认对所有附件做全量正文解析
4. 只有在“模型需要理解附件内容”时，才触发 `parse_file`
5. 如果 skill 的目标是脚本处理原始文件，则直接使用 `/uploads/...` 交给 `run_python`
6. `file_read` 只读 UTF-8 文本文件，不承担 Office/PDF 二进制解析职责

一句话：

> **先上传成文件，再按需决定：解析给模型，还是直接交给脚本。**

---

## 3. 三层职责划分

### 3.1 上传层

上传层只负责：

1. 文件写入 MinIO
2. 记录附件元数据
3. 在进入 runtime 时物化到当前 session 的 `uploads/`

上传层不负责：

1. 默认解析正文
2. 默认抽取全文文本
3. 默认转换成 Markdown

### 3.2 解析层

解析层通过 `parse_file` 能力按需提供：

1. 文本抽取
2. 结构化摘要
3. 表格 / section / heading 等信息
4. 适合模型直接消费的内容视图

解析层不负责：

1. 修改原文件
2. 生成最终交付物
3. 代替脚本执行

### 3.3 执行层

执行层通过 `runtime_tool` 和 `run_python` 负责：

1. 访问 `/uploads` 原始文件
2. 在 `/workspace`、`/temp` 生成中间文件
3. 在 `/outputs` 生成最终产物
4. 通过 `write_artifact` 上传可下载文件

执行层不负责：

1. 把所有二进制附件自动转成文本给模型
2. 让模型直接用 `file_read` 啃 `.docx/.pdf/.xlsx`

---

## 4. 两类典型使用路径

### 4.1 模型需要理解附件内容

适用场景：

1. 总结文档
2. 基于制度附件问答
3. 根据上传 Word 改写、润色、抽取
4. 读取 Excel 内容后做分析

推荐流程：

1. 用户上传附件
2. 系统保存附件元数据，并物化到 `/uploads`
3. 在真正需要读取内容时，调用 `parse_file`
4. 解析结果注入模型上下文
5. 模型基于解析结果回答

特点：

1. 模型消费的是“解析结果”
2. 原始二进制文件仍然保留
3. 不要求模型直接读取 `.docx/.pdf/.xlsx`

### 4.2 脚本直接处理原始附件

适用场景：

1. docx 翻译并重新生成对照文档
2. Excel 模板批量处理
3. PDF 合并、拆分、转图片
4. Office 文件高保真导出

推荐流程：

1. 用户上传附件
2. 系统保存附件元数据，并物化到 `/uploads`
3. skill 直接调用 `run_python`
4. 脚本读取 `/uploads/...`
5. 在 `/outputs` 生成结果
6. 通过 `write_artifact` 输出最终交付物

特点：

1. 模型未必需要阅读正文
2. 附件只是脚本输入文件
3. 对高保真处理更稳定

---

## 5. 什么时候触发 `parse_file`

### 5.1 应触发的情况

以下情况应触发按需解析：

1. 用户问题本身要求“阅读附件内容”
2. 模型需要基于附件正文给出回答
3. skill 的核心能力是文档理解，而不是文件加工
4. 用户明确说“总结”“提取”“问答”“分析”“改写”“根据附件回答”

### 5.2 不应触发的情况

以下情况不应默认触发解析：

1. 只是把附件交给脚本处理
2. 只是做文件转换、导出、生成产物
3. 只是检查文件是否存在
4. 只是列目录、查看文件元信息
5. skill 已经明确会用 `run_python` / 专用解析脚本处理原件

### 5.3 当前推荐规则

当前阶段建议采用简单规则：

1. 附件上传后默认不立即全文解析
2. 只有聊天链路或 skill 明确需要“附件内容理解”时，才触发 `parse_file`
3. Office/PDF 这类二进制文件不再鼓励通过 `file_read` 直接读取

---

## 6. 文件对象协议

附件在系统中的第一身份是“文件对象”，而不是“文本内容”。

建议统一元数据字段：

```json
{
  "id": "file-uuid",
  "fileName": "制度说明.docx",
  "extension": ".docx",
  "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "size": 12747,
  "objectName": "chat-files/1/uuid.docx",
  "logicalPath": "/uploads/制度说明.docx"
}
```

说明：

1. `logicalPath` 用于 runtime
2. `objectName` 用于对象存储
3. `fileName` 用于前端显示和产物命名
4. 是否解析正文，不由这层决定

---

## 7. `parse_file` 协议建议

### 7.1 目标

`parse_file` 的职责是：

1. 将文件转换为适合模型消费的内容
2. 保留必要结构信息
3. 避免模型直接读取二进制原件

### 7.2 输入建议

```json
{
  "file": {
    "fileName": "制度说明.docx",
    "logicalPath": "/uploads/制度说明.docx",
    "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  },
  "mode": "markdown | text | structured",
  "maxSections": 20
}
```

### 7.3 输出建议

```json
{
  "success": true,
  "fileName": "制度说明.docx",
  "fileType": "docx",
  "contentView": {
    "mode": "structured",
    "text": "...",
    "markdown": "...",
    "sections": [
      {
        "type": "paragraph",
        "index": 1,
        "text": "..."
      }
    ]
  },
  "warnings": []
}
```

### 7.4 设计原则

1. `parse_file` 输出面向模型消费
2. 不保证和原文档百分百高保真一致
3. 若需要高保真编辑或重建，仍应走脚本执行

---

## 8. `file_read` 与 `parse_file` 的边界

### `file_read` 负责

1. 读取 UTF-8 文本文件
2. 读取工作区中的 `.md/.txt/.json/.py/.yaml` 等
3. 读取 skill 定义中的文本资源

### `file_read` 不负责

1. 解析 `.docx`
2. 解析 `.pdf`
3. 解析 `.xlsx`
4. 解析图片
5. 任何“二进制转模型可读内容”的动作

### `parse_file` 负责

1. 将附件内容转成模型可读视图
2. 对 Word / Excel / PDF 等格式做解析
3. 提供结构化 section、表格、文本结果

### `run_python` 负责

1. 读取原始附件
2. 进行高保真处理
3. 生成最终文件

---

## 9. 与当前实现的关系

当前代码里已经有一部分能力可复用：

1. 上传文件写入 MinIO
2. runtime 前物化附件到 `/uploads`
3. 附件预解析能力：
   - `AttachmentParseService`
   - `WordAttachmentParser`
   - `ExcelAttachmentParser`
4. `run_python` 可直接读取 `/uploads/...`

当前需要调整的核心口径是：

1. 不再把“预解析所有附件”当成默认行为
2. 将现有 `AttachmentParseService` 收敛为按需 `parse_file`
3. `file_read` 明确只服务文本文件
4. skill 模板里区分“需要理解内容”和“需要处理原件”两条路径

---

## 10. 对 skill 的硬规则

后续所有 skill 都应遵守以下规则：

1. 上传附件首先把它当作文件对象，而不是默认文本
2. 只有在模型需要理解附件内容时，才调用 `parse_file`
3. 若任务本质是文件加工或导出，优先直接使用 `/uploads/...` + `run_python`
4. 不要对 `.docx/.pdf/.xlsx` 默认使用 `file_read`
5. 若系统已有附件解析结果，应优先使用解析结果，而不是重复读取原件

---

## 11. 推荐的统一工作流

推荐在 Lingz Agent Runtime 中采用以下决策顺序：

1. 用户上传附件
2. 系统记录文件对象元数据
3. runtime 物化到 `/uploads`
4. 判断当前任务属于哪一类：
   - 需要理解内容
   - 需要脚本处理原件
5. 若需要理解内容：
   - 调 `parse_file`
   - 将解析结果提供给模型
6. 若需要脚本处理原件：
   - 直接调 `run_python`
   - 产物写到 `/outputs`
   - 再 `write_artifact`

一句话：

> **先判断任务要“读内容”还是“处理文件”，再决定走 `parse_file` 还是 `run_python`。**

---

## 12. 最终结论

Lingz 的附件入口规范建议正式定为：

1. 附件默认是文件对象，不是默认文本
2. 附件上传后不强制全文解析
3. 需要模型理解内容时，再按需调用 `parse_file`
4. 需要高保真处理时，直接将 `/uploads/...` 交给 `run_python`
5. `file_read` 只负责文本文件，不负责二进制附件解析

这样可以同时满足：

1. 模型理解型场景
2. 脚本高保真处理场景
3. runtime 边界清晰
4. 后续统一到 native / docker / remote runtime
