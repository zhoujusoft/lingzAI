# parse_file 详细设计

**版本**: v1.0  
**日期**: 2026-04-28  
**状态**: 设计定稿  
**适用范围**: Lingz Agent Runtime 中附件内容按需解析能力 `parse_file`

---

## 1. 目标

`parse_file` 是 Lingz 平台级附件解析能力，用于在“模型确实需要理解附件内容”时，将原始文件转换为可供模型消费的文本或结构化视图。

本设计解决以下问题：

1. `parse_file` 的输入输出协议如何定义
2. `parse_file` 应该返回什么状态，如何表达降级
3. `MarkItDown`、现有特化 parser、自定义脚本之间如何分流
4. 当解析失败、部分成功、格式不支持时，系统如何继续工作
5. 如何与 `runtime_tool`、`run_python`、当前 `AttachmentParseService` 对齐

一句话目标：

> **把附件解析能力从“隐式预解析”升级为“显式按需调用、可降级、可观测的 parse_file 能力”。**

---

## 2. 结论先行

`parse_file` 的设计原则直接定为：

1. `parse_file` 是平台能力，不是某个 skill 的私有能力
2. 默认实现优先可采用 `MarkItDown`
3. 对特定格式允许优先走平台特化 parser
4. `parse_file` 只负责“内容理解视图”，不负责高保真编辑
5. `parse_file` 失败不等于整个任务失败
6. 若任务本质是文件加工，应直接走 `/uploads + run_python`

一句话：

> **parse_file 用于“把文件讲给模型听”，不是替代脚本去“操作文件本身”。**

---

## 3. 角色与边界

### 3.1 `parse_file` 负责

1. 读取附件原始文件
2. 抽取文本、章节、表格、标题等内容视图
3. 输出 Markdown / text / structured 结果
4. 返回解析质量和告警信息

### 3.2 `parse_file` 不负责

1. 修改原始附件
2. 输出最终可下载文档
3. 执行高保真 Office 重建
4. 代替 `run_python`

### 3.3 `parse_file` 与其他能力的边界

- `file_read`
  - 只读 UTF-8 文本文件
  - 不承担二进制解析

- `parse_file`
  - 负责把二进制附件转成模型可读内容

- `run_python`
  - 负责对原始文件做高保真处理、重建、导出

- `write_artifact`
  - 负责最终交付

---

## 4. 何时调用

### 4.1 应调用 `parse_file` 的场景

1. 用户让模型阅读文档内容
2. 用户让模型基于附件回答问题
3. 用户让模型总结、提取、改写、分析附件内容
4. skill 需要模型理解正文，而不是只处理原文件

### 4.2 不应调用 `parse_file` 的场景

1. 任务本质是文件加工
2. 脚本可以直接处理原始文件
3. 只是做导出、转换、排版重建
4. 只是检查文件元信息、列目录、确认文件存在

### 4.3 当前推荐决策顺序

1. 先判断任务是“内容理解型”还是“文件处理型”
2. 内容理解型走 `parse_file`
3. 文件处理型跳过 `parse_file`，直接交给 `run_python`
4. 混合型优先对“需要理解的部分”解析，对“需要高保真的部分”走脚本

---

## 5. 输入协议

建议 `parse_file` 输入统一为：

```json
{
  "file": {
    "fileName": "报销制度.docx",
    "logicalPath": "/uploads/报销制度.docx",
    "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "size": 12747
  },
  "mode": "structured",
  "options": {
    "maxSections": 20,
    "includeTables": true,
    "includeHeadings": true,
    "preferredParser": "auto"
  }
}
```

字段说明：

### `file`

必须包含：

1. `fileName`
2. `logicalPath`

建议包含：

1. `contentType`
2. `size`

### `mode`

建议支持：

1. `text`
   - 返回纯文本视图
2. `markdown`
   - 返回 Markdown 视图
3. `structured`
   - 返回结构化 section / table / heading 视图

默认建议：

1. 聊天问答默认 `structured`
2. 简单总结可使用 `markdown`

### `options`

当前阶段建议支持：

1. `maxSections`
2. `includeTables`
3. `includeHeadings`
4. `preferredParser`

当前阶段不建议让模型直接控制：

1. 任意系统参数
2. 解释器参数
3. 底层命令行选项

---

## 6. 输出协议

建议统一输出：

```json
{
  "success": true,
  "status": "SUCCESS",
  "fileName": "报销制度.docx",
  "fileType": "docx",
  "parser": "markitdown",
  "mode": "structured",
  "summary": {
    "paragraphCount": 24,
    "tableCount": 2,
    "sheetCount": 0,
    "sectionCount": 14
  },
  "contentView": {
    "text": "...",
    "markdown": "...",
    "sections": [
      {
        "type": "paragraph",
        "index": 1,
        "name": "",
        "text": "..."
      }
    ]
  },
  "warnings": [],
  "error": "",
  "qualityHint": "NORMAL"
}
```

---

## 7. 状态码设计

### 7.1 业务状态

建议 `status` 只允许以下值：

1. `SUCCESS`
   - 解析成功，结果可正常使用

2. `PARTIAL`
   - 解析成功，但内容可能不完整或结构损失明显

3. `UNSUPPORTED`
   - 当前格式或当前平台能力不支持解析

4. `FAILED`
   - 理论支持，但本次解析失败

### 7.2 `success` 字段规则

建议：

1. `SUCCESS` -> `success=true`
2. `PARTIAL` -> `success=true`
3. `UNSUPPORTED` -> `success=false`
4. `FAILED` -> `success=false`

理由：

1. `PARTIAL` 结果通常仍可供模型部分消费
2. `UNSUPPORTED/FAILED` 不应伪装成成功

### 7.3 `qualityHint`

建议补一个简单质量提示：

1. `HIGH`
2. `NORMAL`
3. `LOW`
4. `UNKNOWN`

作用：

1. 给模型和上层 orchestration 一个轻量判断信号
2. 不要求算法绝对精准，但要有边界提示

---

## 8. 降级策略

### 8.1 parser 选择降级顺序

建议内部执行顺序：

1. 显式指定的特化 parser
2. 平台特化 parser
   - 如 `WordAttachmentParser`
   - 如 `ExcelAttachmentParser`
3. `MarkItDown`
4. 返回 `UNSUPPORTED` 或 `FAILED`

说明：

1. 特化 parser 优先保证质量
2. `MarkItDown` 作为平台通用解析基座
3. 不把所有格式都死绑到 `MarkItDown`

### 8.2 内容质量降级

当解析结果不完整时：

1. 返回 `PARTIAL`
2. 填充 `warnings`
3. 设置 `qualityHint=LOW` 或 `NORMAL`
4. 允许上层继续使用结果

典型情况：

1. 复杂表格只提取了部分结构
2. 页眉页脚被忽略
3. 部分 section 被截断
4. 只保留了文本，不保留版式

### 8.3 运行链路降级

若 `parse_file` 失败：

1. 对内容理解型任务
   - 明确告诉模型“当前正文解析不足”
   - 不允许硬编正文内容

2. 对文件处理型任务
   - 允许直接继续走 `run_python`
   - 不因 `parse_file` 失败中断整个任务

3. 对混合任务
   - 能理解的部分继续理解
   - 需要高保真的部分交给脚本

### 8.4 平台能力降级

若 `MarkItDown` 不可用：

1. 先尝试平台特化 parser
2. 特化 parser 也不可用时，返回 `UNSUPPORTED`
3. 不要在运行时临时装包
4. 不要让 skill 直接感知底层解析引擎异常细节

---

## 9. 与现有结构的映射

当前仓库已有：

1. `AttachmentParser`
2. `AttachmentParserFactory`
3. `AttachmentParseResult`
4. `AttachmentParseService`

这套结构可以作为 `parse_file` Phase 1 的基础。

### 9.1 当前结构可直接复用

- `AttachmentParser`
  - 作为特化 parser SPI

- `AttachmentParserFactory`
  - 作为 parser 选择入口

- `AttachmentParseResult`
  - 可作为 `structured` 模式的基础数据模型

### 9.2 需要补的字段

当前 `AttachmentParseResult` 还缺：

1. `status`
2. `parser`
3. `qualityHint`
4. `mode`
5. 可选的 `markdown` / `text` 输出位

### 9.3 Phase 1 建议做法

第一阶段不推翻现有结构，建议：

1. 保留 `AttachmentParseResult`
2. 外层新增 `parse_file` 结果包装对象
3. 将 `AttachmentParseResult` 放入 `contentView.sections/entities/summary`
4. 新增状态、parser、qualityHint 等统一元数据

---

## 10. 与 MarkItDown 的关系

### 10.1 定位

`MarkItDown` 的定位应是：

1. 平台默认通用解析引擎
2. `parse_file` 的一个 provider
3. 面向模型理解内容的转换层

不应定位为：

1. skill 私有依赖
2. 高保真 Office 重建方案
3. runtime_tool 的替代品

### 10.2 推荐使用方式

推荐：

1. 服务环境默认安装 `markitdown`
2. 平台通过 `MarkItDownParseProvider` 统一调用
3. skill 只知道 `parse_file`，不直接依赖 `markitdown`

### 10.3 为什么还要保留特化 parser

原因：

1. 某些格式你已有更稳的 parser
2. 某些业务需要更结构化的字段
3. `MarkItDown` 适合通用解析，但不一定总是最优

---

## 11. 推荐的实现结构

建议新增一层抽象：

### `FileParseService`

职责：

1. 对外提供统一 `parse_file`
2. 管理 parser/provider 选择
3. 管理状态与降级逻辑

### `FileParseProvider`

职责：

1. 描述某个解析实现
2. 判断自己支持哪些格式
3. 执行实际解析

建议实现：

1. `SpecializedAttachmentParseProvider`
2. `MarkItDownParseProvider`

### `FileParseResult`

职责：

1. 统一包装 `status`
2. 统一包装 `parser`
3. 统一包装 `qualityHint`
4. 携带 `contentView`

---

## 12. 错误码建议

建议统一错误码：

1. `PARSE_FILE_UNSUPPORTED`
2. `PARSE_FILE_FAILED`
3. `PARSE_FILE_TIMEOUT`
4. `PARSE_FILE_INPUT_NOT_FOUND`
5. `PARSE_FILE_PROVIDER_UNAVAILABLE`
6. `PARSE_FILE_OUTPUT_EMPTY`

说明：

1. `UNSUPPORTED` 表示平台或格式不支持
2. `FAILED` 表示支持但执行失败
3. `PROVIDER_UNAVAILABLE` 表示底层 provider 缺失或依赖未就绪

---

## 13. 对模型的使用规则

后续系统提示词和 skill 模板建议统一成：

1. 上传附件首先把它看成文件对象
2. 如果需要理解附件内容，调用 `parse_file`
3. 如果只是处理原始文件，直接使用 `/uploads/...` 配合 `run_python`
4. 不要对 `.docx/.pdf/.xlsx` 默认调用 `file_read`
5. 若 `parse_file` 返回 `PARTIAL`，要带着不确定性使用结果
6. 若 `parse_file` 返回 `FAILED/UNSUPPORTED`，不要编造正文内容

---

## 14. Phase 规划

### Phase 1

1. 将现有 `AttachmentParseService` 收敛成可显式调用的 `parse_file`
2. 保留现有 Word / Excel parser
3. 去掉“默认预解析注入上下文”的强依赖
4. `file_read` 明确只读文本

### Phase 2

1. 接入 `MarkItDownParseProvider`
2. 建立 provider 选择逻辑
3. 增加 `status/parser/qualityHint`
4. 增加解析能力自检

### Phase 3

1. 增加管理面能力矩阵
2. 支持更多格式与特化 parser
3. 完善缓存、超时、限流与观测

---

## 15. 最终结论

`parse_file` 应作为 Lingz 的平台级附件解析能力存在，并遵守以下原则：

1. 按需调用，不默认全量解析
2. 用于模型理解内容，不用于高保真文件处理
3. 默认可由 `MarkItDown` 提供通用解析能力
4. 特化 parser 优先，通用 parser 兜底
5. 解析失败不等于任务失败
6. `run_python` 继续负责原始文件加工

这样可以把附件链路稳定收敛为：

1. 上传成文件对象
2. 判断任务类型
3. 需要理解内容时走 `parse_file`
4. 需要高保真处理时走 `run_python`
