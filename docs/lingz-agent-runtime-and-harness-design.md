# Lingz Agent Runtime 与 Harness 设计

**版本**: v1.0  
**日期**: 2026-04-27  
**状态**: 设计草案  
**适用范围**: Lingz 全部 Agent 场景，包括普通聊天、技能聊天、知识库问答、数据集问数、API 填报、技能工坊、后续个人助理 Agent

> 落地补充：P0 已按 profile/capability 方式完成 Runtime 分层骨架，详见 [agent-runtime-profile-capability-architecture.md](agent-runtime-profile-capability-architecture.md)。

---

## 1. 设计目标

本文档用于回答两个问题：

1. Lingz 的 Agent Runtime 这一层到底要统一什么
2. Harness 应该放在什么位置、按什么优先级落地

结论先说：

> Lingz 后续应先统一 Agent Runtime 外壳，再把 Harness 能力分阶段装进去。

也就是说：

- Runtime 负责统一任务入口、执行主链路、输出事件流、会话存储与工作区边界
- Harness 负责让这套 Runtime 从“会回答”进化到“能完成任务”

---

## 2. 一句话定义

### 2.1 Runtime

Runtime 是 Lingz 的统一执行壳。

它解决的是：

- 任务从哪里进入
- 执行链路怎么走
- 输出事件怎么吐
- 会话怎么存
- 文件和工具边界怎么控

### 2.2 Harness

Harness 是围绕 Runtime 的可靠性系统。

它解决的是：

- 给模型什么上下文
- 模型能调用哪些工具
- 结果怎么验证
- 失败后怎么修复
- 状态如何延续
- 整个过程怎么观测和回放

### 2.3 二者关系

一句话：

> Runtime 是骨架，Harness 是内脏，Agent 体验是最终表现层。

没有统一 Runtime，Harness 很容易碎片化；没有 Harness，Runtime 只是一个统一的聊天壳。

---

## 3. 为什么当前第一优先级是 Runtime

结合当前仓库已有设计文档，可以看到 Lingz 已经逐步收敛出 Runtime 的三个基础面：

1. 会话与事件流
   - [session-level-sse-event-stream-evolution.md](/Users/xiehb/workspace/lingzhou-agent/docs/session-level-sse-event-stream-evolution.md)
2. 会话、消息、事件三层模型
   - [conversation-model-and-memory-design.md](/Users/xiehb/workspace/lingzhou-agent/docs/conversation-model-and-memory-design.md)
3. 工作区与执行隔离
   - [workspace-design.md](/Users/xiehb/workspace/lingzhou-agent/docs/sandbox/workspace-design.md)
   - [runtime-discussion-conclusions.md](/Users/xiehb/workspace/lingzhou-agent/docs/sandbox/runtime-discussion-conclusions.md)
   - [lingclaw-runtime-architecture-design.md](/Users/xiehb/workspace/lingzhou-agent/docs/sandbox/lingclaw-runtime-architecture-design.md)
   - [lingclaw-runtime-native-design.md](/Users/xiehb/workspace/lingzhou-agent/docs/sandbox/lingclaw-runtime-native-design.md)
   - [lingclaw-runtime-docker-design.md](/Users/xiehb/workspace/lingzhou-agent/docs/sandbox/lingclaw-runtime-docker-design.md)

这说明：

> Lingz 当前最缺的不是单点 AI 能力，而是一套统一的 Runtime 主链路。

如果任务入口、输出协议、会话模型、工作区边界还没统一，直接做复杂 Harness，只会导致：

- 每个场景自己拼一套上下文
- 每个场景自己吐一套 SSE
- 每个场景自己存一套消息结构
- 每个场景自己维护文件边界

这会让后续技能工坊、知识问答、数据集问数、个人助理 Agent 都越来越分叉。

---

## 4. 结合现有 sandbox 设计的总体原则

Lingz 的 Agent Runtime 不应该脱离 `docs/sandbox` 再平行起一套说法，而应该明确继承当前已经收敛出的结论：

1. **工作区目录语义继续使用项目自己的设计**
   - `/workspaces/public/...`
   - `/workspaces/users/<userId>/sessions/<sessionId>/...`
2. **沙盒执行内核吸收 `lingclaw`**
   - 多根 `sandboxRoot`
   - `native` 模式下的 `PathJail`
   - `docker` 模式下的容器执行内核
   - shell 安全拦截与路径逃逸拦截
3. **统一运行时操作入口采用 `coding_tool` 思路**
   - 文件读取
   - 文件编辑
   - 目录查看
   - 内容搜索
   - shell / bash 执行
4. **运行模式演进顺序沿用现有结论**
   - `native`
   - `docker`
   - `docker + GUI`

一句话：

> 工作区语义用 Lingz 自己的，沙盒执行内核用 lingclaw 的。

---

## 5. Lingz Runtime 的职责边界

Lingz Agent Runtime 这一层建议统一负责以下内容。

### 5.1 统一任务入口

所有场景统一进入同一种 Runtime Request。

包括但不限于：

- 普通聊天
- 技能聊天
- 已发布技能聊天
- 知识库问答
- 数据集问数
- API 填报
- 技能工坊
- 后续个人助理 Agent

它们不应该再有多套“各自专属的聊天入口协议”。

### 5.2 统一执行主链路

统一执行主链路建议固定为：

1. 接收任务
2. 解析会话范围与作用域
3. 组装上下文
4. 选择执行模式 / 绑定 skill
5. 注入工具
6. 调用模型
7. 执行工具
8. 输出事件
9. 持久化消息与事件
10. 返回结果或进入下一步

### 5.3 统一输出事件流

后续所有 Agent 场景都应复用同一套事件协议，而不是按页面或业务场景新发明一套事件。

第一阶段建议统一为：

- `session_meta`
- `message_started`
- `assistant_chunk`
- `tool_started`
- `tool_finished`
- `tool_failed`
- `artifact_generated`
- `message_completed`
- `message_failed`
- `conversation_done`

后续再逐步演进为更完整的会话级事件流。

### 5.4 统一会话存储模型

后续统一收敛到三层模型：

1. `conversation_session`
2. `conversation_message`
3. `conversation_event`

其中：

- `conversation_session` 是会话容器
- `conversation_message` 是主展示节点
- `conversation_event` 是运行层真相

### 5.5 统一工作区和执行边界

Runtime 必须统一定义：

- 当前任务默认工作目录
- 可读目录
- 可写目录
- 上传目录
- 输出目录
- 临时目录
- 日志目录
- profile 目录
- skill 定义目录
- 公共工坊区是否可见

这也是 `native` / `docker` 两种执行模式的共同上层抽象。

---

## 6. Execution Layer 在 Lingz 中怎么落

结合现有 `docs/sandbox` 设计，Runtime 的执行层不应该抽象成一层空概念，而应该是：

> 一套统一 Runtime Request + 两种 backend + 一个统一 `coding_tool` 入口

### 6.1 backend 不是两套业务协议

`native` 与 `docker` 只是执行 backend，不是两套聊天协议，也不是两套工具协议。

上层必须保持不变：

- 同一套 Runtime Request
- 同一套 Runtime Event
- 同一套 Workspace Root
- 同一套 Tool 语义

### 6.2 `coding_tool` 作为统一运行时操作入口

结合 [runtime-discussion-conclusions.md](/Users/xiehb/workspace/lingzhou-agent/docs/sandbox/runtime-discussion-conclusions.md)，后续 Lingz 应尽量减少“专用执行工具”，把运行时操作统一收口到 `coding_tool` 一类抽象。

典型动作包括：

- `file_read`
- `file_edit`
- `list_dir`
- `search`
- `bash`

这样：

- 文件工具语义统一
- 命令执行语义统一
- Python / Node / CLI 执行能力下沉为 runtime 默认环境能力

### 6.3 Python 不是单独 tool，而是 runtime 默认能力

后续不应继续围绕 `runPythonScript` 这类专用业务工具扩展。

更合理的方式是：

- Python 是 runtime 环境能力
- 真实执行通过 `coding_tool(action="bash")`
- `native` / `docker` 决定在哪执行，不改变上层调用形态

### 6.4 推荐演进顺序

沿用当前 sandbox 讨论结论：

1. `native`
2. `docker`
3. `docker + GUI`

这条顺序的意义是：

- 第一阶段先打通本地工作区与权限模型
- 第二阶段升级到更强隔离
- 第三阶段再补需要 GUI 的复杂任务

---

## 7. Harness 在 Lingz 中的落点

当 Runtime 外壳统一后，Harness 才能稳定落进去。

### 5.1 Context

Context 是信息供给系统。

在 Lingz 中建议拆成：

- 系统上下文
- 用户上下文
- 会话上下文
- scope 上下文
- skill 上下文
- 工作区上下文
- 检索增强上下文

### 5.2 Tools

Tools 是能力边界系统。

在 Lingz 中建议统一为：

- 文件工具
- API 工具
- 数据集工具
- 知识库工具
- MCP 工具
- 脚本 / shell 工具
- Artifact 工具
- 前端渲染工具

关键不是工具数量，而是：

- 注册方式统一
- schema 统一
- 权限统一
- 日志统一

### 5.3 State

State 是任务连续推进系统。

建议分为：

- 会话状态
- 当前运行状态
- 工作区状态
- 用户长期状态
- 项目 / 任务长期状态

### 5.4 Validation

Validation 是结果可信机制。

建议覆盖：

- 输出结构校验
- 文件存在性校验
- artifact 可读性校验
- 工具结果状态校验
- 关键操作人工确认

### 5.5 Feedback Loop

Feedback Loop 是错误闭环系统。

建议支持：

- 失败转事件
- 失败转下一轮输入
- 工具失败自动重试
- 校验失败回退到修复
- 人工确认后恢复执行

### 5.6 Observability

Observability 是可调试系统。

建议沉淀：

- Runtime Request
- Context 组装摘要
- Tool timeline
- Message timeline
- Token 消耗
- 耗时
- 失败点
- 最终产物摘要

---

## 8. Runtime 与 Harness 的优先级排序

如果当前第一目标是：

> 统一任务入口 + 统一输出扣子

那么建议优先级如下。

### P0：统一 Runtime 外壳

必须先统一：

1. 任务入口协议
2. 输出事件流
3. 会话 / 消息 / 事件模型
4. 工作区模型
5. `coding_tool` 操作入口
6. `native` / `docker` 的统一执行抽象

### P1：最小 Harness 闭环

优先补：

1. Context
2. Tools
3. State
4. Observability

原因：

- 没有 Context，统一入口没意义
- 没有 Tools，Agent 还是只能生成文本
- 没有 State，任务无法延续
- 没有 Observability，系统不可调试

### P2：增强型 Harness

再补：

1. Validation
2. Feedback Loop
3. Human-in-the-loop

这一步会让 Lingz 从“会回答”进化到“能稳定完成任务”。

### P3：长期 Agent 能力

最后补：

1. 用户长期记忆
2. 项目长期记忆
3. 持续观察型任务
4. 异步补充回复
5. 多轮计划执行

---

## 9. 建议的统一 Runtime 分层

建议后续 Lingz Runtime 按四层理解。

### 7.1 接入层

职责：

- 接收统一 Runtime Request
- 建立或找到会话
- 识别 sessionType / scopeType / scopeId

### 7.2 编排层

职责：

- 组装 Context
- 选择 Mode / Skill / Persona
- 注入 Tools
- 生成 Execution Plan

### 9.3 执行层

职责：

- native 执行
- docker 执行
- `coding_tool` 动作执行
- 文件系统读写
- shell / script 执行
- artifact 生成

### 7.4 输出与存储层

职责：

- 输出统一 SSE 事件
- 写入 `conversation_message`
- 写入 `conversation_event`
- 写入 `tool_trace`
- 写入 artifact 与工作区状态

---

## 10. 第一阶段建议聚焦的三个统一协议

如果现在要落第一刀，建议不要一次性讲完整 Agent，而是先把这三个东西定死。

### 8.1 Lingz Runtime Request

统一描述“任务如何进入系统”。

### 8.2 Lingz Runtime Event

统一描述“系统如何向前端和存储层表达执行过程”。

### 8.3 Lingz Runtime Workspace

统一描述“执行过程中能读写什么、写到哪里、怎么隔离”。

只要这三层稳定，后续：

- Harness
- Skill Runtime
- 技能工坊
- 个人助理 Agent

都可以在其上继续演进，而不用反复推翻底层协议。

---

## 11. 与现有 sandbox 文档的关系

本文档不是替代 `docs/sandbox`，而是站在更上层做统一收口。

关系应当是：

1. `workspace-design.md`
   - 定义工作区目录语义
2. `lingclaw-runtime-architecture-design.md`
   - 定义“工作区语义由项目定义，执行内核吸收 lingclaw”
3. `lingclaw-runtime-native-design.md`
   - 定义 `native` backend
4. `lingclaw-runtime-docker-design.md`
   - 定义 `docker` backend
5. 本文档
   - 定义 Runtime 如何作为整个 Lingz Agent 平台的统一外壳
   - 定义 Harness 如何分阶段装进这套 Runtime

也就是说：

> `docs/sandbox` 负责回答“怎么安全执行”，本文档负责回答“这套执行系统在 Lingz Runtime 里处于什么位置”。

---

## 12. 结论

Lingz 后续不应该继续演进成“很多 AI 能力的集合”，而应该收敛成：

> 一套统一的 Agent Runtime 平台。

这套平台的第一阶段重点不是把 Harness 做满，而是：

1. 统一任务入口
2. 统一输出事件流
3. 统一会话与消息模型
4. 统一工作区与执行边界

在此基础上，再逐步把：

- Context
- Tools
- State
- Validation
- Feedback Loop
- Observability

这些 Harness 能力装进去。

一句话总结：

> Runtime 决定 Lingz 有没有统一底盘，Harness 决定这套底盘能不能真正跑成可靠 Agent。
