# Personal Agent Runtime V2 设计方案

> 更新说明（2026-05-24）：
> 本文档主要描述 Runtime V2 的总体设计与 graph 骨架引入背景。
> 关于后续 `v1 / v2 classic / v2 graph` 的职责边界、`classic` 冻结退役策略，以及 `classic -> graph` 的正式迁移计划，请以 [v2-graph-runtime-migration-plan.md](./v2-graph-runtime-migration-plan.md) 为准。

## 1. 背景

当前个人 Agent 聊天链路已经具备以下能力：

- 会话管理
- 流式输出
- 工具调用展示
- 产物展示与预览
- 技能上下文恢复
- token / usage 持久化
- 代码执行态恢复
- 运行时快照与步骤记录

现有主链路的核心问题不在于功能缺失，而在于运行时控制权不足。

当前链路本质上是：

1. 首轮由系统完成外部编排
2. 进入工具调用后，由 Spring AI 内部 tool loop 接管后续轮次
3. 系统无法对第 2/N 轮做到完整的外部控制

这导致以下限制：

- 后续轮次无法稳定复用系统自定义的动态 prompt 装配
- 每轮工具边界无法完全按阶段动态切换
- `iterationCount` 与 `llmCallCount` 统计容易混淆
- summarize / escalation / finalization 只能部分外置
- direct / react / code / plan 等模式难以统一在一个显式状态机内

因此需要新增一条独立的 Runtime V2 链路，在不影响现有链路的前提下，收回 loop 控制权。

## 2. 目标

Runtime V2 的目标不是替换现有链路，而是新增一条可独立演进的运行时体系。

目标如下：

1. 保持现有前端聊天交互风格基本一致
2. 新增一条由系统显式控制 loop 的执行链路
3. 支持按阶段动态组装 prompt，而不是只在首轮组装
4. 支持按阶段动态暴露工具，而不是一次性暴露全量工具
5. 统一承载 `direct / react / code / plan` 等模式
6. 明确区分 `iterationCount` 与 `llmCallCount`
7. 保持消息持久化结构与现有前端历史回放兼容
8. 不影响现有 `/chat` 和老页面使用

## 3. 非目标

本次 V2 改造不以以下内容为首要目标：

- 不直接替换现有 `/chat` 主链路
- 不在首期强行覆盖所有旧场景
- 不要求一次性实现全部高级模式
- 不要求首期完成完整 approval / replay / delegate 体系
- 不要求首期重做整个前端聊天 UI

## 4. 总体策略

采用“双轨并行”的演进策略：

- 保留 V1：
  - 现有 `/chat`
  - 现有页面
  - 现有 Spring AI 内部 tool loop
- 新增 V2：
  - 新页面
  - 新 endpoint
  - 新 runtime loop controller
  - 新 prompt / phase / mode 编排体系

核心原则：

- 新能力全部在 V2 中收口
- 老链路保持不动
- 前端尽量复用现有消息流组件
- 后端尽量复用现有共享基础设施

## 5. 核心设计原则

### 5.1 loop owner 在系统外部

V2 不再依赖模型内部隐式工具循环，而由系统显式控制：

- 何时调用模型
- 何时执行工具
- 何时进入 observation
- 何时进入 summarize
- 何时结束

### 5.2 prompt 每轮动态组装

prompt 不再只在首轮组装一次，而是每一轮依据当前 state 重组。

### 5.3 工具边界按阶段动态切换

不同阶段、不同模式暴露不同工具集合。

### 5.4 前端协议兼容优先

V2 优先兼容现有 SSE 事件语义和历史消息结构，尽量不重写前端核心聊天 UI。

### 5.5 V2 编排代码独立收口

V2 的主流程代码统一放入 `v2` 包，避免继续与 V1 深度缠绕。

### 5.6 入口与执行引擎解耦

V2 不应继续由单个超大 orchestrator 既承担入口，又承担 loop 控制、工具执行、code 升级、最终收口。

当前实现状态（2026-05-24）：

- `ChatConversationV2Service` 只依赖 `RuntimeV2ExecutionGateway`
- `RuntimeV2ExecutionGateway` 已只注册并调度 graph engine
- 请求侧 `runtimeV2Engine` 透传已移除
- `RuntimeV2Orchestrator` 已删除，V2 不再保留 classic 执行壳

这样后续把 reasoning / action / observation / finalize 迁入 graph 节点时，不需要再修改聊天入口层。

## 6. 运行模式

V2 统一支持以下模式：

### 6.1 direct

适用场景：

- 简单问答
- 纯解释
- 不需要工具
- 不需要多轮观察

特点：

- prompt 最轻
- 通常单次模型调用
- 不进入复杂 loop

### 6.2 react

适用场景：

- 需要边观察边执行
- 可能需要多轮工具调用
- 任务目标相对单一

特点：

- 外部显式 loop
- reasoning -> action -> observation 循环
- 可中途升级到 code

### 6.3 code

适用场景：

- 明确涉及代码编辑、终端执行、测试构建
- 工具世界无法完成
- 需要 workspace / shell / patch 能力

特点：

- 工具集更偏工程执行
- prompt 偏执行和验证
- 可由 react 中途升级进入

### 6.4 plan

适用场景：

- 多步骤任务
- 存在明显先后依赖
- 需要先规划再执行

特点：

- 更强调 step 级编排
- 首期可延后实现

## 7. 运行阶段

V2 建议统一使用以下 phase：

- `TRIAGE`
- `MODE_SELECTED`
- `REASONING`
- `ACTION`
- `OBSERVATION`
- `SUMMARIZING`
- `ESCALATING`
- `FINALIZING`
- `COMPLETED`
- `FAILED`
- `INTERRUPTED`

说明：

- `TRIAGE`：轻量分流，确定是否 direct/react/code/plan
- `REASONING`：单轮模型决策
- `ACTION`：执行本轮工具
- `OBSERVATION`：归并工具输出，形成下一轮上下文
- `SUMMARIZING`：压缩 observation 历史
- `ESCALATING`：从 react/tool 世界升级到 code 世界
- `FINALIZING`：收敛最终回答和产物
- `COMPLETED/FAILED/INTERRUPTED`：终态

## 7.1 当前迁移落点

截至当前实现，V2 已具备以下演进骨架：

- `RuntimeV2ExecutionGateway`
- `RuntimeV2ExecutionSessionFactory`
- `RuntimeV2GraphEngine`
- 基于 `spring-ai-alibaba-graph-core` 的 graph 主链

当前已收口到 graph 主链的能力包括：

- 统一运行前准备
- graph event -> SSE 投影
- personal agent preflight 前置控制
- run / usage 主链闭环
- V2 入口 graph-only 调度

当前仍需继续增强的能力包括：

- approval / replay / resume
- plan-execute 子图
- 更完整的 timeline / tool / phase 事实统一

因此 graph 现在已经不是“仅可挂载的骨架”，而是 V2 的唯一生产执行主链。

## 8. 状态模型

Runtime V2 统一维护 `RuntimeV2State`。

建议核心字段包括：

- request/session/user 信息
- 当前 mode
- 当前 phase
- finishReason
- iterationCount
- llmCallCount
- toolCallCount
- userMessage
- currentAnswerDraft
- currentObservation
- observationSummary
- toolCalls
- toolResults
- toolEvents
- segments
- phaseTrace
- usageMeta
- runtimeMeta

关键约束：

- `iterationCount` 表示完整循环次数
- `llmCallCount` 表示真实模型调用次数
- summarize 可能增加 `llmCallCount`，但不一定增加 `iterationCount`

## 9. Prompt 组装体系

V2 不采用单一大 prompt，而采用 block 化组装方式。

建议 block：

- `Identity Block`
- `Mode Block`
- `Phase Block`
- `Tool Policy Block`
- `Skill Context Block`
- `Memory Block`
- `Observation Block`
- `Safety / Budget Block`
- `Output Contract Block`

不同阶段的组装策略：

### TRIAGE

仅放：

- 轻量身份
- 基础路由规则
- 当前用户消息
- 少量 routing 级工具

### REASONING

放：

- 当前模式规则
- 当前阶段规则
- 当前 observation
- 当前可见工具边界
- 当前必要上下文

### SUMMARIZING

放 summarize 专用约束，只做压缩，不做任务执行。

### FINALIZING

放最终回答格式、artifact 汇总规则、结束条件。

## 10. 工具策略

V2 工具暴露建议按层管理：

### routing tools

适用于 TRIAGE：

- 能力探测
- 技能发现
- 基础资源查询

### react tools

适用于 REACT：

- 业务工具
- 技能工具
- runtime tool
- 知识库 / 数据集工具

### code tools

适用于 CODE：

- 文件读取
- 文件编辑
- 终端执行
- 测试 / 构建
- sandbox / workspace 能力

约束：

- TRIAGE 不应直接暴露高权限 code 工具
- FINALIZING 阶段不再开放额外工具
- 工具策略必须可按 phase 和 mode 动态切换

## 11. 升级策略

V2 需要显式支持从 react/tool 世界升级到 code 世界。

建议触发条件包括：

- 用户明确要求改代码
- 工具世界无法完成目标
- `ToolToCodeEscalationPolicy` 命中
- 多轮工具调用后仍无有效进展
- 明确需要 workspace / shell / patch

升级后进入：

- `ESCALATING -> CODE`

## 12. SSE 协议兼容策略

V2 必须优先兼容现有前端事件语义。

必须兼容的事件：

- `meta`
- `message`
- `tool`
- `result`
- `citation`
- `fallback_notice`
- `error`
- `done`

可新增但不应作为基础依赖的事件：

- `phase-plan`
- `phase-progress`
- `runtime-state`
- `budget`
- `usage-snapshot`

原则：

- 老事件名尽量保持不变
- 新能力优先通过 payload 扩展字段表达
- 前端旧逻辑无需理解所有新字段也能正常渲染

## 13. 持久化兼容策略

V2 不仅要兼容流式输出，还必须兼容刷新后的历史消息回放。

必须继续产出：

- `content`
- `segmentsJson`
- `paramsJson`
- `artifactSummaryJson`
- `promptTokens`
- `completionTokens`
- `totalTokens`
- `llmCallCount`
- `toolCallCount`
- `usageSummaryJson`

建议在 `paramsJson` 中新增 V2 字段：

- `runtimeVersion`
- `mode`
- `phaseTrace`
- `finishReason`
- `iterationCount`
- `llmCallCount`
- `toolEvents`
- `promptProfile`
- `escalationTrace`
- `summaryTrace`

原则：

- 老页面即使不理解这些字段，也不能出错
- 新页面可在此基础上做更丰富展示

## 14. 前端策略

V2 采用“新页面 + 旧聊天壳复用”的方式。

建议新增：

- `FrontAgentChatV2Page`
- `FrontAgentChatV2Workspace`
- `generalChatV2Adapter`

继续复用：

- `FrontChatWorkspace`
- `ChatMessageStream`
- 现有 artifact/html preview 体系
- 现有消息历史与会话侧边栏逻辑

目标：

- 保持用户交互习惯基本一致
- 差异主要体现在 runtime 状态展示和右侧 insight 扩展区

## 15. 后端包结构策略

原则：

- V2 的主流程代码全部放进 `v2`
- 共享底层能力不复制，通过 facade 接入

建议目录：

- `business/chatv2/...`
- `capability/agentruntime/v2/state`
- `capability/agentruntime/v2/loop`
- `capability/agentruntime/v2/triage`
- `capability/agentruntime/v2/prompt`
- `capability/agentruntime/v2/reasoning`
- `capability/agentruntime/v2/action`
- `capability/agentruntime/v2/observation`
- `capability/agentruntime/v2/event`
- `capability/agentruntime/v2/persistence`
- `capability/agentruntime/v2/context`

共享基础设施保留原位，例如：

- 会话历史服务
- 技能上下文恢复服务
- 现有 artifact/tool 持久化能力
- token quota 能力
- personal snapshot 能力

## 16. 首期范围

首期建议只实现：

- 新页面入口
- 新 `/chat/v2` endpoint
- `direct` 和 `react`
- 外部显式 loop
- 兼容现有 SSE 协议
- 兼容现有历史消息回放结构
- 分离 `iterationCount` 与 `llmCallCount`

首期不强求：

- `plan`
- 完整 summarize 体系
- 复杂 approval / replay
- 全量 code escalation 自动化
- 复杂 insight 可视化面板

## 17. 验收标准

首期建议验收以下三项：

1. V2 下可以完成多轮模型调用与多次工具调用，且 `llmCallCount` 与真实模型调用一致
2. 流式展示与刷新后的历史回放表现一致，不出现 tool / artifact 卡片丢失
3. 现有 `/chat` 与老页面完全不受影响

## 18. 风险与控制

### 风险 1：V2 与 V1 逻辑继续相互耦合

控制策略：

- V2 主流程统一收口到 `v2` 包
- 共享能力通过 facade 接入
- 不在 V1 orchestrator 上继续堆分支

### 风险 2：前端刷新后展示与流式展示不一致

控制策略：

- V2 持久化必须继续产出兼容的 `segmentsJson / toolEvents / artifactSummaryJson`

### 风险 3：一开始设计过大，落地速度过慢

控制策略：

- 首期只做 `direct + react`
- summarize / code / plan 分阶段接入

## 19. 结论

本次改造建议采用：

- 老链路不动
- 新页面承接
- 新 runtime 独立
- 外部显式 loop
- 每轮动态 prompt
- 协议兼容优先

建议将其作为 `Personal Agent Runtime V2` 独立推进，而不是继续在现有主链路上做增量修补。
