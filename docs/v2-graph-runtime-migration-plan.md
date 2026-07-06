# V2 Graph Runtime 迁移与收口方案

**版本**: v1.0  
**日期**: 2026-05-24  
**状态**: 执行中  
**适用范围**: Lingz 运行时演进、`v2 classic` 退役、`v1` 快聊保留、`v2 graph` 任务模式建设

> 更新说明（2026-05-24）：
> `RuntimeV2ExecutionGateway` 已切到 graph-only；
> `runtimeV2Engine` 请求透传已移除；
> `RuntimeV2Orchestrator` 与 classic 专属测试已从代码库删除。

关联文档：

- [personal-agent-runtime-v2-design.md](./personal-agent-runtime-v2-design.md)
- [lingz-agent-runtime-and-harness-design.md](./lingz-agent-runtime-and-harness-design.md)
- [agent-runtime-execution-prd.md](./agent-runtime-execution-prd.md)

---

## 1. 背景

当前 Lingz 实际上存在三套运行时能力：

### 1.1 V1

基于 Spring AI 托管工具调用链路。

特点：

- 路径短
- 接入简单
- 已具备稳定快聊能力
- 更适合 `Quick Chat`

限制：

- loop 控制权弱
- 难以承载复杂任务编排
- 不适合作为后续任务型能力演进主链

### 1.2 V2 Classic

由系统自行控制 loop 的运行时链路。

特点：

- 已具备较强执行控制能力
- 已接入较多 runtime 状态恢复与执行能力
- 当前成熟度高于 graph

限制：

- 主流程仍偏“大 orchestrator + 隐式 loop”
- phase 不够显式
- 审批、恢复、计划执行、delegation 等能力扩展不自然
- 长期继续演进会形成新的大泥球

### 1.3 V2 Graph

基于图状态机的 agent runtime。

当前已具备：

- `triage`
- `reasoning`
- `action`
- `observation`
- `code escalation`
- `final answer`

当前定位：

- 已是最适合承接任务模式的主线
- 但能力尚未完全追平 `v2 classic`
- 当前仍处于演进与迁移阶段

---

## 2. 本文档结论

本次运行时演进的正式结论如下：

1. `v1` 保留为 `Quick Chat` 运行时。
2. `v1` 后续不再新增 agent 能力。
3. `v1` 只允许跟随公共层改造：
   - 会话
   - 消息
   - 统一 SSE 协议
   - 历史存储
   - 公共鉴权 / quota / model 绑定等底座能力
4. `v2 classic` 立即进入冻结状态。
5. `v2 classic` 不再新增任何新能力。
6. `v2 graph` 承接 `v2 classic` 的全部能力迁移。
7. `v2 graph` 承接后续所有任务模式能力演进。
8. 后续新能力默认只允许进入 `v2 graph`。

一句话：

> `v1` 保快聊，`v2 graph` 保任务与未来演进，`v2 classic` 冻结并最终删除。

---

## 3. 目标架构

## 3.1 产品语义

产品层只保留两种运行模式语义：

- `CHAT`
- `TASK`

其中：

- `CHAT` 默认由 `v1` 承接
- `TASK` 默认由 `v2 graph` 承接

禁止继续以“`v1 / v2 classic / v2 graph`”作为产品层交互语义暴露给前端或业务页面。

---

## 3.2 技术分工

### V1 Runtime

职责：

- 快速对话
- 轻量工具调用
- 低状态开销
- 低延迟优先

限制：

- 不新增任务型能力
- 不接入新型 approval / replay / delegation / workflow 能力
- 不承接复杂文件、代码、产物执行链的后续演进

### V2 Graph Runtime

职责：

- 任务模式主链
- 多步推理与执行
- phase 可观测
- code/file/artifact 执行
- 审批 / 恢复 / replay / resume
- 后续 delegation / workflow

要求：

- 成为唯一可持续演进的任务型 runtime
- 承接 `v2 classic` 的全部任务型能力

### V2 Classic Runtime

职责：

- 迁移期兼容
- 线上兜底
- 必要 bugfix

限制：

- 不新增能力
- 不新增任务型分支
- 不再作为目标架构的一部分

---

## 4. 迁移原则

## 4.1 不是“图替代一切”，而是“图承接任务主链”

本次迁移的目标不是让 `v2 graph` 一次性替代全部运行时。

真正目标是：

1. `v1` 持续承接快聊
2. `v2 graph` 接管任务主链
3. `v2 classic` 的能力全部平移到 `v2 graph`
4. `v2 classic` 最终下线

---

## 4.2 不是代码搬家，而是能力平移

迁移判断标准不是“某个类删了没有”，而是“某项能力是否已在 graph 主链可用”。

因此必须先做：

1. `classic` 能力清单
2. graph 对应落点清单
3. 每项能力迁移状态标记：
   - `未迁移`
   - `部分迁移`
   - `已迁移`

---

## 4.3 冻结 classic，停止吸收新需求

从本文档生效起：

- 禁止继续向 `v2 classic` 新增任务型能力
- 禁止出现“graph 和 classic 同时补一套新能力”的开发策略
- 若某新需求只有在 classic 中容易实现，也应优先调整设计而不是把能力继续做进 classic

---

## 5. 当前关键代码落点

当前主要链路如下：

### 5.1 统一请求组装

- [backend/src/main/java/lingzhou/agent/backend/business/chat/runtime/ChatRuntimeRequestMapper.java](../backend/src/main/java/lingzhou/agent/backend/business/chat/runtime/ChatRuntimeRequestMapper.java)
- [backend/src/main/java/lingzhou/agent/backend/business/chat/runtime/ChatRuntimePreparedRequestAssembler.java](../backend/src/main/java/lingzhou/agent/backend/business/chat/runtime/ChatRuntimePreparedRequestAssembler.java)

### 5.2 V1 / 旧链路参考

- [backend/src/main/java/lingzhou/agent/backend/business/chat/service/ChatRuntimeExecutor.java](../backend/src/main/java/lingzhou/agent/backend/business/chat/service/ChatRuntimeExecutor.java)

说明：

- `RuntimeV2Orchestrator` 已删除，不再作为 V2 运行时入口或兼容执行壳保留。

### 5.3 Graph 入口与会话工厂

- [backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/engine/RuntimeV2ExecutionGateway.java](../backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/engine/RuntimeV2ExecutionGateway.java)
- [backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/engine/RuntimeV2ExecutionSessionFactory.java](../backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/engine/RuntimeV2ExecutionSessionFactory.java)

### 5.4 Graph 状态机

- [backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/graph/RuntimeV2GraphBuilder.java](../backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/graph/RuntimeV2GraphBuilder.java)
- [backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/graph/state/RuntimeV2GraphStateKeys.java](../backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/graph/state/RuntimeV2GraphStateKeys.java)
- [backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/graph/node/RuntimeV2TriageNode.java](../backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/graph/node/RuntimeV2TriageNode.java)
- [backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/graph/node/RuntimeV2ReasoningNode.java](../backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/graph/node/RuntimeV2ReasoningNode.java)
- [backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/graph/node/RuntimeV2ActionNode.java](../backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/graph/node/RuntimeV2ActionNode.java)
- [backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/graph/node/RuntimeV2ObservationNode.java](../backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/graph/node/RuntimeV2ObservationNode.java)
- [backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/graph/node/RuntimeV2FinalAnswerNode.java](../backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/v2/graph/node/RuntimeV2FinalAnswerNode.java)

### 5.5 执行面

- [backend/src/main/java/lingzhou/agent/backend/business/chat/execution/RuntimeExecutionFacade.java](../backend/src/main/java/lingzhou/agent/backend/business/chat/execution/RuntimeExecutionFacade.java)

---

## 6. Graph 迁移目标能力

`v2 graph` 需要承接的不是某一个类，而是以下能力集合：

1. 会话初始化与消息持久化
2. `chatModel` 恢复
3. `loadedSkills` 恢复
4. personal agent snapshot 注入
5. personal agent precheck
6. quota 校验
7. request-scoped skill kit
8. runtime tool callback 绑定
9. code execution state 恢复
10. runId / runCode / runType
11. SSE 事件统一投影
12. tool / result / phase / timeline 持久化
13. usage 统计与落库
14. runtime workspace / uploads / temp sync
15. `file_write / run_python / write_artifact / bash` 执行链
16. 失败持久化与友好终态
17. 后续 approval / replay / resume
18. 后续 plan-execute / delegation / workflow

---

## 7. MateClaw 对标参考

`mateclaw` 不应整体照搬，但其 graph runtime 有 4 个非常适合直接借鉴的点。

## 7.1 显式状态键体系

参考：

- [mateclaw-server/src/main/java/vip/mate/agent/graph/state/MateClawStateKeys.java](../mateclaw-server/src/main/java/vip/mate/agent/graph/state/MateClawStateKeys.java)

借鉴点：

- 所有状态键集中声明
- 节点和 dispatcher 全部引用统一常量
- 避免字符串散落
- 为状态键注册覆盖测试留出基础

适用于 Lingz 的目标：

- `RuntimeV2GraphStateKeys` 成为 graph 唯一状态键入口
- 为每个 key 显式注册 strategy
- 增加 coverage test，防止漏注册

## 7.2 Graph 事件发布器

参考：

- [mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java](../mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java)

借鉴点：

- graph 节点先写事实事件
- agent / SSE 层再消费这些事件
- 事件类型明确且可演进

适用于 Lingz 的目标：

- 先形成统一 graph ledger event
- 再统一投影到 `meta/message/tool/result/error/done/phase`
- 后续支持 `finish_reason / feedback / perf_summary / plan_step`

## 7.3 ReAct 与 Plan-Execute 分图

参考：

- [mateclaw-server/src/main/java/vip/mate/agent/graph/StateGraphReActAgent.java](../mateclaw-server/src/main/java/vip/mate/agent/graph/StateGraphReActAgent.java)
- [mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java](../mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java)

借鉴点：

- 快速 ReAct 与任务分步执行不强行塞进一张图
- 复杂任务可显式拥有：
  - plan
  - step execute
  - final summary

适用于 Lingz 的目标：

- 首期先把当前 graph ReAct 主链补完整
- 二期再新增 `task-plan-execute` 子图或新 engine

## 7.4 审批与恢复工作流

参考：

- [mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java](../mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/delegation/SubagentController.java](../mateclaw-server/src/main/java/vip/mate/agent/delegation/SubagentController.java)

借鉴点：

- pending 持久化
- 启动恢复
- timeout GC
- approve / deny 后 replay
- 子任务控制面

适用于 Lingz 的目标：

- approval 不放在业务层零散处理
- 作为 graph runtime 原生能力建设

---

## 8. P0-P2 迁移 Backlog

## 8.1 P0：把 Graph 变成可承接主链的 Runtime

### P0-1 冻结 Classic

目标：

- `classic` 停止新增能力
- 后续新需求默认禁止落 classic

涉及落点：

- `ChatRuntimeExecutor`

验收标准：

- 后续开发约束明确写入设计文档
- 新功能实现不再以 classic 为默认落点

### P0-2 固化 Graph State Keys

目标：

- `RuntimeV2GraphStateKeys` 成为唯一 graph 状态键入口
- graph builder 为每个 state key 显式注册 strategy

参考：

- `MateClawStateKeys`

验收标准：

- 所有参与 graph 的 key 都集中声明
- 新增状态键漏注册时测试会失败

### P0-3 抽出 Graph Event Ledger

目标：

- graph 事件先写事实，再投影 SSE

参考：

- `GraphEventPublisher`

验收标准：

- 至少统一以下事件事实源：
  - `phase`
  - `tool_call_started`
  - `tool_call_completed`
  - `finish_reason`
  - `feedback`
  - `error`

### P0-4 统一运行前准备

目标：

- graph 主链在进入执行前完成所有必要恢复和绑定

涉及能力：

- `loadedSkills` 恢复
- `chatModel` 恢复
- run 建立
- workspace 准备
- tool callback 绑定

目标落点：

- `RuntimeV2ExecutionSessionFactory`

验收标准：

- graph 主链不再依赖 classic 做这些前置工作

### P0-5 收口 Personal Agent 前置逻辑

目标：

- personal agent snapshot / precheck / planning event 不再依赖 classic 主链

验收标准：

- graph 入口具备与 classic 等价的前置控制能力

---

## 8.2 P1：把 Classic 核心能力迁平到 Graph

### P1-1 迁移 Quota / Failure / Usage

目标：

- graph 独立完成 quota 校验
- graph 独立完成失败消息持久化
- graph 独立完成 usage 落库

验收标准：

- graph 完成后不再需要 classic 兜底这些终态行为

### P1-2 迁移 Code Execution State 恢复

目标：

- graph 能从历史消息恢复执行态

验收标准：

- `codeExecutionActive` 等执行态由 graph 自行恢复

### P1-3 完整接入 Execution Plane

目标：

- `file_write / run_python / write_artifact / bash` 全链统一走 execution facade

验收标准：

- graph 不再旁路执行面
- workspace / uploads / temp sync 全部可用

### P1-4 统一 Tool / Message / Timeline 事实源

目标：

- 历史回放、SSE、run trace、tool trace、phase trace 同源

验收标准：

- 不再依赖多套来源拼装回放结果

### P1-5 去除 Graph 对 Classic 的运行时回退依赖

目标：

- `TASK` 模式可以稳定不依赖 classic fallback

验收标准：

- graph 默认执行不再回退 classic
- `RuntimeV2ExecutionGateway` 不再读取请求级 engine 选择
- V2 生产代码中不再保留 classic engine 实现

---

## 8.3 P2：把 Graph 做成唯一扩展面

### P2-1 建设 Task Plan-Execute 子图

目标：

- graph 从增强 ReAct 升级为任务型 runtime

参考：

- `StateGraphPlanExecuteAgent`

验收标准：

- 支持：
  - plan
  - step execute
  - final summary

### P2-2 建设 Approval / Replay / Resume

目标：

- approval 成为 graph 原生能力

参考：

- `ApprovalWorkflowService`

验收标准：

- pending 持久化
- timeout
- approve / deny 后 replay
- 重启恢复

### P2-3 收编任务型旁路线

优先对象：

- skill preview
- 文件处理
- 产物生成
- 后续 skillstudio task 场景

验收标准：

- 任务型入口不再自带独立执行链

### P2-4 建设 Delegation / Workflow

目标：

- graph 承接后续子任务与流程编排

参考：

- `SubagentController`

验收标准：

- 父子 run、状态、中断、恢复有统一控制面

---

## 9. Classic 删除门槛

只有同时满足以下条件，`v2 classic` 才能删除：

1. `TASK` 默认且仅走 `v2 graph`
2. `graph` 不再依赖 classic fallback
3. `classic` 独有能力项清零
4. 新需求连续一个版本周期未触碰 classic
5. 回放、usage、phase、tool trace 均由 graph 主链产出

---

## 10. Classic -> Graph 能力迁移矩阵

下表中的“当前来源”表示该能力当前主要由 classic 或旧 runtime 主链承接；“目标落点”表示后续 graph 收口后的主要归属位置。

| 能力 | 当前来源 | Graph 目标落点 | 当前状态 |
| --- | --- | --- | --- |
| `loadedSkills` 恢复 | `ChatRuntimeExecutor` + `RuntimeSkillStateRecoveryService` | `RuntimeV2ExecutionSessionFactory` | 部分迁移 |
| `chatModel` 恢复 | `ChatRuntimeExecutor` | `RuntimeV2ExecutionSessionFactory` | 部分迁移 |
| run 建立与 `runId/runCode/runType` 注入 | `RuntimeV2ExecutionSessionFactory` + classic 持久化流程 | `RuntimeV2ExecutionSessionFactory` + `RuntimeV2GraphEngine` | 部分迁移 |
| quota 校验 | `ChatRuntimeExecutor` / graph 执行入口 | `RuntimeV2ExecutionGateway` 或 graph 执行入口统一前置 | 部分迁移 |
| personal agent snapshot enrich | `ChatRuntimeExecutor` | graph 执行入口前置 | 未迁移 |
| personal agent precheck | `AgentRuntimeOrchestrator` | graph 执行入口前置或 graph 专用 preflight | 未迁移 |
| planning event 记录 | `ChatRuntimeExecutor` | graph 执行入口前置 | 未迁移 |
| request-scoped skill kit 构建 | `RuntimeV2ExecutionSessionFactory` | `RuntimeV2ExecutionSessionFactory` | 已具备骨架 |
| runtime workspace 准备 | `RuntimeExecutionCapabilityAdapter` / classic 主链 | `RuntimeV2ExecutionSessionFactory` | 部分迁移 |
| runtime tool callback 绑定 | `RuntimeExecutionCapabilityAdapter` / classic 主链 | `RuntimeV2ExecutionSessionFactory` | 部分迁移 |
| tool callback skill execution scope 包装 | `RuntimeV2ExecutionSessionFactory` | `RuntimeV2ExecutionSessionFactory` | 已具备骨架 |
| code execution state 恢复 | `ChatRuntimeExecutor` | graph 执行入口前置或 session factory | 未迁移 |
| tool event 持久化 | graph 主链 | `RuntimeV2ActionNode` + `RuntimeV2GraphEngine` | 部分迁移 |
| timeline text segment 持久化 | graph 主链 | `RuntimeV2ReasoningNode` / `RuntimeV2GraphEngine` | 部分迁移 |
| phase trace 持久化 | graph 主链 | `RuntimeV2GraphEngine` | 部分迁移 |
| 成功终态持久化 | graph 主链 | `RuntimeV2GraphEngine.persistSuccess` | 部分迁移 |
| 失败终态持久化 | graph 主链 | `RuntimeV2GraphEngine.persistFailure` | 部分迁移 |
| usage 统计与落库 | graph 主链 | `RuntimeV2GraphEngine` | 部分迁移 |
| `file_write/run_python/write_artifact/bash` 执行 | classic + `RuntimeExecutionFacade` | `RuntimeExecutionFacade` 经 graph 主链调用 | 部分迁移 |
| 完整 SSE 事件投影 | `ChatSseEventBuilder` + classic 主链 | graph event ledger -> SSE adapter | 未迁移 |
| classic fallback 兜底 | `RuntimeV2ExecutionGateway` | 删除依赖 | 已迁移 |
| approval / replay / resume | 旁路或未统一 | graph 原生能力 | 未开始 |
| plan-execute / task steps | 未落主链 | graph 子图 / 新 engine | 未开始 |

说明：

1. `已具备骨架` 表示 graph 主链已经存在对应接入点，但能力还未完全与 classic 对齐。
2. `部分迁移` 表示 graph 已有可运行实现，但仍存在 classic 依赖、行为差异或事实源未统一的问题。
3. `未开始` 表示尚未形成统一 graph 主链能力。

---

## 11. 第一批实施切片（建议直接建开发任务）

为了避免一次性改造过大，建议先做一个最小可交付切片：

### Slice A：冻结与可观测基础

目标：

- 冻结 classic
- 固化 graph state keys
- 抽 graph 事实事件层

交付项：

1. 新增 graph state key 覆盖测试
2. 为 `RuntimeV2GraphStateKeys` 所有参与状态注册 strategy
3. 新增 `GraphEvent` / `GraphEventPublisher` 风格抽象
4. graph 节点改为优先记录事实事件，再统一投影 SSE

验收标准：

- 新状态键漏注册会测试失败
- tool/phase 事件不再仅靠即时 SSE 拼装

### Slice B：运行前准备统一收口

目标：

- graph 承接 classic 的运行前准备能力

交付项：

1. `loadedSkills` 恢复统一进 `RuntimeV2ExecutionSessionFactory`
2. `chatModel` 恢复统一进 `RuntimeV2ExecutionSessionFactory`
3. personal agent snapshot enrich 前移到 graph 主入口
4. personal agent precheck 能在 graph 主链终止并持久化
5. code execution state 恢复迁入 graph 主入口

验收标准：

- graph 主链运行前不再依赖 `ChatRuntimeExecutor` 做额外恢复
- personal agent 在 graph 下具备与 classic 对齐的前置控制

### Slice C：执行与终态闭环

目标：

- graph 独立完成一次完整任务运行的执行、落库和失败闭环

交付项：

1. quota 校验前移到 graph 统一入口
2. `RuntimeExecutionFacade` 全量动作由 graph 主链接入
3. graph 独立完成 success/failure/message/usage/run 落库
4. 去掉 graph 对 classic 的默认 fallback 依赖，仅保留灰度开关

验收标准：

- 在 `TASK` 模式下，graph 能独立完成从开始到终态的完整闭环
- 无需 classic 兜底即可运行核心任务链

---

## 12. 建议实施顺序

### 第一阶段

- 冻结 classic
- 固化 graph state keys
- 固化 graph event ledger
- 固化 graph 运行前准备

### 第二阶段

- 迁 quota / usage / failure
- 迁 code execution state 恢复
- 完整接入 execution plane
- 去除 classic fallback 依赖

### 第三阶段

- 增加 plan-execute 子图
- 增加 approval / replay / resume
- 收编任务型旁路线

### 第四阶段

- 增加 delegation / workflow
- 删除 classic

---

## 13. 一句话总结

> Lingz 后续运行时演进的主线不是继续维护三套 runtime，而是让 `v1` 稳定承接快聊，让 `v2 graph` 成为唯一任务型 runtime，并把 `v2 classic` 的全部能力平移后删除。
