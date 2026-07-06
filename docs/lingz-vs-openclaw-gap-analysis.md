# Lingz 与 OpenClaw 差距分析

**日期**: 2026-05-20  
**目的**: 评估当前 `Lingz` 相对 `OpenClaw` 的能力差距，明确“我们已经有什么”和“下一步最该补什么”。

---

## 1. 结论

如果把 `OpenClaw` 看成“通用 agent operating system”，那 `Lingz` 当前的差距主要不在 RAG、技能、数据集、表单这些业务能力，而在：

1. 持续执行能力
2. 多 agent / sub-agent 编排能力
3. 安全审批与质量闸门
4. 浏览器执行能力的一等接入
5. 长期记忆治理
6. 插件/生态分发

更直白一点：

- `Lingz` 更像：**强业务能力 + 半成品 runtime**
- `OpenClaw` 更像：**强 runtime / orchestration / safety / ecosystem 的通用 agent 平台**

---

## 2. Lingz 当前已经具备的能力

### 2.1 统一 Runtime 骨架已经存在

`Lingz` 并不是没有 runtime，而是已经有了明显的统一底座：

- 已有 runtime 画像与 profile 分流：
  - `GENERAL_CHAT`
  - `SKILL_CHAT`
  - `SKILL_STUDIO`
  - `DATASET_CHAT`
  - `PERSONAL_ASSISTANT`
- 参考：
  - [AgentRuntimeProfileResolver.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/AgentRuntimeProfileResolver.java:14)

代码中已经明确把个人 Agent 请求分流到 `PERSONAL_ASSISTANT`，并让其进入 `TOOL_AWARE` pipeline，而不是继续完全走普通聊天模式。

### 2.2 Capability 装配层已经有了

当前注册表中已经定义了较完整的 capability 槽位：

- `TOOL_CALLING`
- `RUNTIME_EXECUTION`
- `EVENT_PERSISTENCE`
- `TOKEN_USAGE`
- `OBSERVABILITY`
- `SAFETY_GUARD`
- `LONG_TERM_MEMORY`
- `QUALITY_GATE`
- `SUB_AGENT`
- `TASK_EXECUTION`

参考：

- [StaticAgentRuntimeProfileRegistry.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/StaticAgentRuntimeProfileRegistry.java:13)

这说明架构思路已经从“一个大聊天类”进入“可分层能力装配”的方向。

### 2.3 Runtime 文件与执行工具已经比较完整

当前 runtime 侧已经有一套独立工具：

- `file_read`
- `file_write`
- `list_dir`
- `stat`
- `run_python`
- `write_artifact`

参考：

- [RuntimeSystemToolProvider.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/business/chat/execution/tool/RuntimeSystemToolProvider.java:18)

说明 `Lingz` 已经不是“纯对话 agent”，而是已经具备受控工作区里的读写、脚本执行、产物输出能力。

### 2.4 个人 Agent 的执行计划与续跑雏形已经存在

当前个人 Agent 不是完全空白，已经有：

- 执行计划
- 预检状态
- 步骤状态
- 事件记录
- 取消 / 失败 / 完成标记

参考：

- [PersonalAgentExecutionSnapshotService.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/personal/PersonalAgentExecutionSnapshotService.java:25)

这说明你们已经开始把“个人 Agent”从纯 prompt 化角色设定，往“任务执行状态机”方向推进。

### 2.5 浏览器 / GUI Sandbox 底座已经开始做

仓库里已经有 Docker GUI / browser sandbox 服务，能：

- 启动浏览器沙箱容器
- 调 `browser_navigate`
- 调 `browser_snapshot`
- 调 `browser_take_screenshot`
- 通过 MCP 风格接口调用浏览器工具

参考：

- [DockerGuiSandboxService.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/sandbox/DockerGuiSandboxService.java:38)

但它现在更像测试服务或底层能力，还没有真正进入统一 agent runtime 主链路。

### 2.6 业务侧能力其实很强

这是 `Lingz` 和很多通用 agent 项目不同的地方。当前项目在业务原语上已经很完整：

- Skill-extended RAG
- 数据集问数
- API 表单填报
- 前端结构化渲染
- SkillStudio
- 技能包导入导出

参考：

- [README.md](/Users/xiehb/workspace/lingzhou-agent/README.md:1)
- [SkillPackageService.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/business/skill/service/SkillPackageService.java:1)

所以 `Lingz` 的短板不是“没有业务能力”，而是“这些业务能力还没有完全被统一 runtime/harness 编排起来”。

---

## 3. Lingz 相对 OpenClaw 的主要差距

## 3.1 缺真正的持续执行能力

`OpenClaw` 的强项之一，是把 agent 执行做成了“可持续、可后台、可恢复”的任务系统，而不是只在当前请求里跑一轮。

典型特征包括：

- detached tasks
- heartbeat / wakeup
- task ledger
- task flow
- 中断后恢复
- 后台异步继续执行

而 `Lingz` 当前虽然已经有：

- 执行计划
- 步骤状态
- 轻量续跑思路

但整体上还是更接近“请求内编排 + 状态补丁”，还不是完整的后台任务执行系统。

OpenClaw 参考：

- [Background Tasks](https://docs.openclaw.ai/automation/tasks)
- [Task Flow](https://docs.openclaw.ai/automation/taskflow)

### 判断

这是当前最大的 gap 之一。

---

## 3.2 缺真正可用的 Sub-Agent 编排

虽然 `Lingz` 在 profile 和 capability 上已经预留了 `SUB_AGENT`，但代码里这个能力目前仍然是 `NOOP`。

参考：

- [SubAgentCapabilityAdapter.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/capabilities/SubAgentCapabilityAdapter.java:8)

这意味着当前还缺：

- spawn 子代理
- 子代理独立上下文
- 子代理独立工具集
- 子代理结果回传
- 中途 steer / cancel / kill
- 主代理和子代理协作协议

而 `OpenClaw` 的 sub-agent 是比较核心的执行原语之一。

OpenClaw 参考：

- [Sub-Agents](https://docs.openclaw.ai/tools/subagents)

### 判断

你们不是没有这个方向，而是还没有真正落地。

---

## 3.3 缺真正的长期记忆治理

`Lingz` 当前已经有：

- 用户 profile 目录
- `memory.md`
- 会话压缩 / 摘要

但 capability 层面的长期记忆目前还是 `NOOP`。

参考：

- [LongTermMemoryCapabilityAdapter.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/capabilities/LongTermMemoryCapabilityAdapter.java:8)

这说明目前更像：

- 有文件载体
- 有局部摘要
- 有设计方向

但还没有形成完整闭环：

- 记忆抽取
- 记忆写回
- 检索召回
- relevance/priority 治理
- 用户可见/可编辑/可追踪

而 `OpenClaw` 已经把记忆做成更完整的 wiki / plugin 体系。

OpenClaw 参考：

- [Memory Wiki](https://docs.openclaw.ai/plugins/memory-wiki)

### 判断

当前 `Lingz` 的长期记忆更像“预留的数据面”，还不是成熟的 runtime capability。

---

## 3.4 缺安全审批与执行闸门

当前 `Lingz` 的 `SafetyGuard` 还是 `NOOP`。

参考：

- [SafetyGuardCapabilityAdapter.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/capabilities/SafetyGuardCapabilityAdapter.java:8)

这意味着现在虽然已经有：

- 工具使用约束
- skill world / general world 边界
- code escalation 的 prompt 级控制

但还缺 runtime 级的硬闸门：

- 宿主命令审批
- 高风险操作 allowlist / denylist
- 分级权限执行
- 用户确认策略
- 审计与追责视图

`OpenClaw` 在这方面更成熟，尤其对 host exec、approval、权限闸门有完整工具层支持。

OpenClaw 参考：

- [Exec Approvals](https://docs.openclaw.ai/tools/exec-approvals)

### 判断

你们现在主要靠 prompt 和局部工具限制，而不是完整的 safety capability。

---

## 3.5 缺质量门与自动验证层

`Lingz` 当前的 `QualityGate` 也是 `NOOP`。

参考：

- [QualityGateCapabilityAdapter.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/capabilities/QualityGateCapabilityAdapter.java:8)

这意味着现在 agent 产物的正确性更多依赖：

- prompt 自觉
- 工具返回是否成功
- 人类二次确认

而缺少更成熟的 runtime 级验证链路，例如：

- 结果后验验证
- artifact existence / schema validation
- 回答与产物一致性检查
- 工具结果与最终答案对齐检查
- 自动 retry / repair 策略

### 判断

这会直接影响复杂执行任务的稳定率。

---

## 3.6 浏览器能力还没真正接入主线

`Lingz` 已经有 browser / GUI sandbox 的原型能力，但目前主要体现在：

- sandbox-test 控制器
- 独立 service
- 浏览器工具单独调用

参考：

- [SandboxTestController.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/business/sandbox/controller/SandboxTestController.java:1)
- [DockerGuiSandboxService.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/sandbox/DockerGuiSandboxService.java:38)

还没有达到 `OpenClaw` 那种状态：

- 浏览器是一等 runtime tool
- 可作为 agent 执行默认手段
- 支持 profile / target / remote browser 统一配置
- 与主执行链路自然集成

OpenClaw 参考：

- [Browser](https://docs.openclaw.ai/tools/browser)

### 判断

你们是“有底层”，但还没有“产品化接入”。

---

## 3.7 缺开放生态与插件分发层

`Lingz` 已经有：

- SkillStudio
- 技能包导入导出
- 运行时技能刷新

这说明内部技能分发体系已经初步形成。

但和 `OpenClaw` 比，仍然缺少公开生态层：

- 公共 registry
- 插件发现
- 插件安装市场
- 版本兼容声明
- 社区生态扩展

OpenClaw 在这方面有更明显的生态产品面。

OpenClaw 参考：

- [ClawHub](https://docs.openclaw.ai/tools/clawhub)

### 判断

你们更像“项目内技能系统”，还不是“通用生态平台”。

---

## 4. 一个很关键的判断：Lingz 的问题不是没有能力，而是能力比较散

这是这次分析里最重要的一点。

`Lingz` 当前不是“什么都没有”，而是：

1. 已经有 runtime 骨架
2. 已经有 capability 分层方向
3. 已经有 skill / dataset / form / artifact / sandbox / memory file 等能力底座
4. 但这些能力还没有被一个成熟的 agent runtime/harness 完整接起来

所以和 `OpenClaw` 的差距，不是单点功能数，而是“系统集成度”：

- `OpenClaw` 更像一个完成度高的 agent OS
- `Lingz` 更像一个业务能力丰富、但 runtime 仍在收敛中的企业 agent 平台

这和你们自己的设计文档判断是一致的：

- 当前最缺的不是单点 AI 能力，而是一套统一 runtime 主链路

参考：

- [docs/lingz-agent-runtime-and-harness-design.md](/Users/xiehb/workspace/lingzhou-agent/docs/lingz-agent-runtime-and-harness-design.md:1)

---

## 5. 现在最缺的能力，按优先级排序

如果目标是尽快缩小和 `OpenClaw` 的 runtime 差距，我建议按下面顺序补。

### P0：把个人 Agent 真正做成“可执行任务体”

目标：

- 不再只是会话内执行计划
- 而是有真正的任务对象 / 任务状态 / 恢复入口 / 后台延续

至少要补：

- task entity
- run / step state
- detached execution
- resume / cancel / retry
- 原会话回写结果

### P1：把 Sub-Agent 从占位变成原生能力

至少要补：

- spawn
- 子代理独立上下文
- 子代理工具边界
- 结果汇总
- kill / cancel / timeout

### P1：把 Browser 真正接到 Runtime Tool Chain

不是继续作为 sandbox-test 存在，而是：

- 作为 runtime capability/tool provider
- 个人 agent 可调用
- skill 也可声明需要 browser
- 与 workspace / artifact / event 一起工作

### P1：把长期记忆从文件载体升级成治理能力

至少要补：

- 记忆抽取
- 记忆写回
- 检索召回
- relevance 策略
- 用户可见编辑

### P1：补 SafetyGuard 与 QualityGate

至少要补：

- 分级审批
- 高风险执行确认
- artifact / result 校验
- 回答与执行结果一致性验证
- 错误恢复和再试策略

---

## 6. 如果只用一句话总结

`OpenClaw` 的领先点是：

> 让 agent 持续、安全、可编排地干活。

`Lingz` 的领先点是：

> 让 agent 更懂企业业务，并能落到数据集、知识库、表单和业务结果。

所以你们现在真正缺的不是“再写几个技能”，而是：

> 把现有 skill、dataset、sandbox、browser、memory 统一装进一套真正能持续执行的 runtime/harness 里。

---

## 7. 外部参考

- OpenClaw Background Tasks:
  - https://docs.openclaw.ai/automation/tasks
- OpenClaw Task Flow:
  - https://docs.openclaw.ai/automation/taskflow
- OpenClaw Sub-Agents:
  - https://docs.openclaw.ai/tools/subagents
- OpenClaw Browser:
  - https://docs.openclaw.ai/tools/browser
- OpenClaw Exec Approvals:
  - https://docs.openclaw.ai/tools/exec-approvals
- OpenClaw Memory Wiki:
  - https://docs.openclaw.ai/plugins/memory-wiki
- OpenClaw ClawHub:
  - https://docs.openclaw.ai/tools/clawhub

---

## 8. 后续建议

如果这份判断成立，下一步建议不要直接做“大而全对标 OpenClaw”，而是拆成三条主线并行推进：

1. `PERSONAL_ASSISTANT -> TASK_EXECUTION` 真后台化
2. Browser / Sandbox 接入统一 runtime
3. Safety / Memory / Quality 三个 NOOP capability 实装

这样最容易把 `Lingz` 从“强业务 agent 平台”推进到“真正能执行工作的 agent 平台”。
