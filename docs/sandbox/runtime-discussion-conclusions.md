# Runtime 与沙盒讨论结论

**版本**: v1.0  
**日期**: 2026-04-22  
**状态**: 讨论结论沉淀  
**适用范围**: 当前项目 runtime / sandbox / workspace / artifact 设计收敛

---

## 1. 本轮讨论的核心结论

本轮讨论最终收敛为以下原则：

1. 工作区目录模型继续使用当前项目自己的设计
2. 沙盒执行内核吸收 `lingclaw` 的优点，不照搬它的整套目录观
3. runtime 演进顺序采用：
   - `native`
   - `docker`
   - `docker + GUI`
4. skill 不一定都需要执行环境
5. Python 不再作为专用工具暴露，而是作为 runtime 默认可用能力
6. `coding_tool` 作为统一运行时操作入口
7. 产物定义为 agent runtime 执行过程中生成的最终交付型结果文件
8. 会话记录与沙盒不冲突，当前会话模型足以支撑第一阶段落地

---

## 2. 关于 Lingclaw 的吸收策略

### 2.1 应该吸收的部分

`lingclaw` 值得吸收的优点包括：

1. 多根 `sandboxRoot` 挂载模型
2. `native` 模式下的 `PathJail` 思路
3. `docker` 模式下的容器执行内核
4. 统一的 `coding_tool` 抽象
5. shell 安全拦截与路径逃逸拦截经验

### 2.2 不应该照搬的部分

`lingclaw` 当前不适合直接照搬的部分包括：

1. `agentId` 级静态沙盒粒度
2. 偏“工程目录挂载”的 runtime 目录观
3. “共享基础 Python 环境即可代表完整 runtime 体系”的做法

### 2.3 结论

最终策略是：

> **目录语义用当前项目自己的，沙盒执行内核吸收 `lingclaw` 的。**

---

## 3. Runtime 演进顺序

推荐演进顺序：

1. `native`
2. `docker`
3. `docker + GUI`

### 3.1 为什么这个顺序合理

原因：

1. `native` 最容易先打通，适合作为第一阶段落地
2. `docker` 是 `native` 的执行 backend 升级，不需要推翻上层协议
3. `docker + GUI` 是 `docker` runtime 的增强版，不是全新体系

### 3.2 是否会冲突

只要一开始就坚持以下原则，后续不会发生架构冲突：

1. workspace 语义固定
2. runtime 抽象固定
3. 工具入口统一
4. 会话记录模型复用
5. 不把 `native` 的具体实现写死到业务层

一句话结论：

> **`native -> docker -> docker + GUI` 是当前项目推荐且不冲突的演进路径。**

---

## 4. Python 执行能力的定位

### 4.1 对 Lingclaw 的理解

`lingclaw` 的 Python 能力主要表现为：

1. Docker 沙盒基础镜像内预装 Python、pip、venv
2. Python 是容器内默认可用的解释器
3. 没有必要额外定义一个 `run_python` 工具
4. 真正执行 Python 时，本质上还是通过 shell 命令完成

### 4.2 对当前项目的结论

当前项目后续应逐步去掉 `runPythonScript` 这类专用业务工具，把 Python 能力下沉到 runtime 默认能力层。

也就是说：

1. Python 不是一个单独的 tool
2. Python 是 runtime 环境的一部分
3. agent 要执行脚本时，通过统一工具入口执行 shell 命令

例如：

```json
{
  "action": "bash",
  "command": "python3 /workspace/scripts/main.py"
}
```

---

## 5. `coding_tool` 的定位

### 5.1 `coding_tool` 是什么

`coding_tool` 是统一运行时操作工具入口，负责承载：

1. 文件读取
2. 文件编辑
3. 目录查看
4. 内容搜索
5. 命令执行

典型动作包括：

1. `file_read`
2. `file_edit`
3. `list_dir`
4. `search`
5. `bash`

### 5.2 哪个动作负责真正执行脚本

真正承担脚本执行职责的是：

- `coding_tool(action="bash")`

也就是说，agent 后续执行 Python / Node / CLI，本质上都是通过 `bash` 完成。

### 5.3 对当前项目的意义

这意味着后续不需要继续保留很多专用执行工具，而是：

1. 文件与命令类能力统一收口到 `coding_tool`
2. Python、Node、CLI 等执行能力下沉为 runtime 默认环境能力

---

## 6. 数据集与 runtime 的关系

当前结论：

1. 数据集服务可以继续独立存在
2. 数据集不需要为了上沙盒而整体搬进 workspace
3. runtime 只负责执行时需要的文件活动范围

建议分工：

1. 长期管理、检索、权限控制的数据继续放数据集服务
2. 临时分析输入、导出产物、中间文件放 runtime workspace
3. 必须让脚本直接读取的文件，再按需挂到 runtime

一句话：

> **数据集服务继续独立存在，runtime 不需要吞掉整套数据集存储语义。**

---

## 7. Lingclaw 的 runtime 文件目录观

### 7.1 它的核心模式

`lingclaw` 的 runtime 目录不是固定平台目录模型，而是：

> **以 `sandboxRoot` 为核心的多根挂载目录模型。**

它的典型规则是：

1. `sandboxRoot[]` 定义 agent 可见目录
2. `sandboxRoot[0]` 作为默认工作目录
3. Docker 模式下第一条 mount 的 `containerPath` 作为容器工作目录
4. 其他目录通过附加 mount 暴露

### 7.2 它没有的部分

它没有像当前项目这样，天然预定义：

1. `workspace/`
2. `uploads/`
3. `outputs/`
4. `temp/`
5. `logs/`
6. `meta/`

所以当前项目不应照搬它的目录观，而是只吸收它的挂载和隔离技术实现。

---

## 8. 会话记录与沙盒是否冲突

### 8.1 结论

当前项目的会话模型和沙盒不冲突，而且能够支撑第一阶段 `native` 落地。

### 8.2 原因

当前项目已经有：

1. `conversation_session`
2. `conversation_message`
3. `conversation_event`
4. `attachments_json`
5. `artifact_summary_json`

这意味着：

1. 会话线程可以继续由 `session` 表示
2. 主回复节点可以继续由 `message` 表示
3. runtime 启动、执行、失败、产物、GUI 操作过程可以放入 `event`

### 8.3 需要补的不是模型，而是运行态元数据

后续可逐步补充：

1. `runtimeMode`
2. `runId`
3. `workspaceId` 或 workspace 摘要
4. `containerId/containerName`
5. 更细的 runtime 事件类型

一句话：

> **会话模型不用为沙盒重做，后续只需要补 runtime 元数据和执行事件。**

---

## 9. Runtime 首次分配的推荐操作过程

推荐原则：

> **先创建会话，再判断是否需要 runtime，再首次分配 runtime。**

### 9.1 推荐时序

1. 用户发送第一条消息
2. 后端创建 `conversation_session`
3. 写入用户消息和 assistant 占位消息
4. 进入路由判断
5. 只有命中 runtime 场景时，才首次分配 workspace
6. 组装 `sandboxRoots`
7. 根据配置选择 `native` / `docker`
8. 初始化 runtime 实例
9. 写入 runtime 事件
10. 开始真正执行

### 9.2 为什么不应该先起沙盒

原因：

1. 不是所有新会话第一句都需要 runtime
2. 提前分配会浪费资源
3. runtime 应该是执行层资源，不是会话存在的前置条件

---

## 10. Runtime 判定怎么做

### 10.1 不能靠什么来判定

runtime 判定不应该依赖：

1. 模型后面会不会临时调用工具
2. 某次推理过程中是否碰巧用了 `bash`

因为这种判定：

1. 太晚
2. 不稳定
3. 不利于提前准备 workspace 和提示词上下文

### 10.2 当前更合理的判定方式

更合理的做法是：

> **根据当前会话/skill/agent 是否进入执行型链路来判定。**

即：

1. 普通聊天，不启 runtime
2. 一旦进入 skill/agent runtime 链路，直接启 runtime

### 10.3 进一步结论

后续应把 skill 分成：

1. 纯编排型 skill
2. runtime 型 skill

也就是说：

> **是否需要 runtime，最终应由 skill/agent 的能力定义决定，而不是由本轮模型临时行为猜测。**

---

## 11. Skill 是否都需要执行环境

结论：

> **不是所有 skill 都需要 runtime。**

### 11.1 不需要 runtime 的 skill

典型包括：

1. 纯对话型 skill
2. 纯结构化输出 skill
3. 只调用现有后端服务/数据集工具/MCP 工具的 skill
4. 只负责信息整合、改写、总结、路由的 skill

### 11.2 需要 runtime 的 skill

典型包括：

1. 需要读写本地文件
2. 需要生成脚本并执行
3. 需要运行 Python / Node / CLI
4. 需要产出本地文件
5. 需要 GUI / 浏览器 / 截图操作

### 11.3 当前建议

第一阶段不必把这套分类做得太重，但应保留扩展位，后续可逐步增加：

1. `requiresRuntime`
2. 或 `runtimeMode = NONE | NATIVE | DOCKER | GUI`

---

## 12. 产物的定义

本轮讨论最终收敛为：

> **产物是 agent runtime 执行过程中生成的、需要交付、展示、下载或再次引用的结果文件。**

### 12.1 第一批明确纳入产物的类型

包括但不限于：

1. 文件类
2. 报告类
3. HTML 类
4. 后续可扩展：
   - `docx`
   - `pdf`
   - `xlsx`
   - `pptx`
   - `html`
   - `md`
   - `json`
   - `png/jpg`
   - `csv`

### 12.2 哪些不算产物

通常不算产物的包括：

1. workspace 中间文件
2. 临时缓存
3. 调试日志
4. 执行过程中的短期中间结果

### 12.3 产物与 skill 的关系

当前结论：

1. 产物实例归 runtime
2. 不是定义在 skill 上的静态实体
3. skill 后续可以声明“预期产物类型”，但不是本轮必须做的事情

换句话说：

> **artifact 首先是 runtime 事实对象，不是 skill 静态对象。**

---

## 13. 产物是否需要入库

结论：

> **需要入库，但入库的是产物元数据，不是大文件正文。**

### 13.1 应入库的内容

建议至少保存：

1. `artifactId`
2. `sessionId`
3. `messageId`
4. `runId`
5. `fileName`
6. `contentType`
7. `storageKey/objectName/path`
8. `size`
9. `runtimeMode`
10. `summary`
11. `createdAt`

### 13.2 不建议直接入库的内容

包括：

1. 大文件正文
2. 图片二进制
3. Word/PDF/Excel 文件内容
4. 大段日志全文

这些应外置到：

1. 对象存储
2. 或 workspace 文件系统

数据库只保存引用和摘要。

### 13.3 当前项目的适配方向

当前项目已经有：

1. `artifactSummaryJson`
2. `writeArtifact(...)`
3. `MinioService`

所以推荐方向是：

1. 产物文件外置
2. 产物元数据入库
3. 主消息只存摘要
4. 事件层记录生成过程

---

## 14. 关于 GUI 沙盒的结论

### 14.1 是否兼容当前路线

兼容。

推荐路径仍然是：

1. `native`
2. `docker`
3. `docker + GUI`

### 14.2 GUI 应该如何定位

GUI 不应被视为一套全新 runtime，而应被视为：

> **Docker runtime 的增强变体。**

### 14.3 设计建议

后续如果引入 GUI 容器：

1. 不污染默认 runtime 镜像
2. 单独维护 `runtime-gui` 镜像
3. 事件层增加 GUI 操作过程记录
4. 截图、页面结果、可下载 HTML 等都可纳入 artifact 体系

---

## 15. 当前阶段的务实推进建议

本轮讨论后的实际推进建议如下：

1. 先上 `native`
2. workspace 继续按当前项目自己的设计
3. 吸收 `lingclaw` 的 `sandboxRoot + PathJail + DockerSandbox + coding_tool` 思路
4. skill runtime 分类先保留扩展位，不急于一次性做重
5. artifact 先按 runtime 事实对象落地
6. 会话模型先不重构，只补 runtime 元数据和事件

一句话总结：

> **先把 runtime 跑起来，再逐步把 skill 分类、artifact 契约、GUI 沙盒这些能力往上加。**

