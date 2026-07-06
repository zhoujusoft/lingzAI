# Lingz vs OpenClaw 一页摘要

**日期**: 2026-05-20  
**用途**: 给老板 / 团队汇报的 1 页版判断

---

## 一句话结论

`Lingz` 现在不是“能力少”，而是“能力散”。  
和 `OpenClaw` 的主要差距，不在 RAG、技能、数据集、表单这些业务能力，而在 **agent runtime / orchestration / safety / 持续执行** 这一层。

---

## 当前判断

### Lingz 的强项

- 更贴近企业业务落地
- 已有技能体系、RAG、数据集问数、表单填报、前端结构化渲染、SkillStudio
- 已有 runtime 骨架、workspace、artifact、执行计划、sandbox 底座

### OpenClaw 的强项

- 更像完整的 agent operating system
- 后台任务、持续执行、子代理、浏览器一等能力、审批闸门、长期记忆、插件生态更成熟

### 本质差异

- `Lingz`：强业务原语，runtime 仍在收敛
- `OpenClaw`：强 runtime / harness / ecosystem

---

## 我们已经有的

1. 统一 runtime 分流骨架
2. capability 装配层
3. 文件读写 / 脚本执行 / 产物输出工具
4. 个人 Agent 执行计划与步骤状态雏形
5. browser / GUI sandbox 底座
6. skill 包导入导出与 SkillStudio

结论：

> 我们不是从 0 开始补 agent runtime，而是在把已有底座收敛成真正可执行的统一系统。

---

## 现在最缺的 5 个能力

### 1. 持续执行能力

当前更多还是“请求内执行”，还不是完整的后台任务系统。

缺：

- detached task
- 后台继续跑
- 中断恢复
- 任务状态机
- 原会话回写结果

### 2. Sub-Agent 编排能力

虽然架构里已经预留 `SUB_AGENT`，但当前仍未真正落地。

缺：

- spawn 子代理
- 子代理独立上下文
- 子代理结果回传
- kill / cancel / timeout

### 3. Safety / Approval 闸门

当前更多靠 prompt 约束和局部工具限制，还没有完整的 runtime 级审批能力。

缺：

- 高风险动作确认
- 宿主命令审批
- 权限分级
- 审计追踪

### 4. Quality Gate / 自动验证

当前复杂执行任务的稳定性，仍主要依赖模型自觉和人工复核。

缺：

- 结果后验验证
- artifact 存在性检查
- 回答与执行结果一致性校验
- 自动 repair / retry

### 5. Browser 一等接入

当前已有 browser sandbox 底层能力，但还没有真正接进 runtime 主链路。

缺：

- 统一 tool 接入
- 个人 agent 可直接调用
- skill 可声明依赖 browser
- browser 与 artifact / event / workspace 联动

---

## 一个关键风险

如果继续只补 skill、补 prompt、补单点业务链路，而不先补 runtime 主链路，后续会越来越分叉：

- 每个场景自己拼执行链
- 每个场景自己管状态
- 每个场景自己搞恢复
- 每个场景自己做工具边界

结果会是：

> 业务能力越来越多，但 agent 真正“能持续干活”的能力增长很慢。

---

## 建议的优先级

### P0

把 `PERSONAL_ASSISTANT -> TASK_EXECUTION` 做成真正的任务执行底座。

目标：

- 从“执行计划”升级成“任务对象 + 运行状态 + 恢复入口 + 后台续跑”

### P1

把 `SUB_AGENT` 从占位变成原生能力。

### P1

把 browser / sandbox 从测试能力接进统一 runtime。

### P1

把 `LONG_TERM_MEMORY / SAFETY_GUARD / QUALITY_GATE` 三个 capability 从 `NOOP` 变成真实能力。

---

## 建议的管理层表述

可以直接这样对外讲：

> `Lingz` 的业务能力底座已经不弱，当前阶段的核心任务不是再堆更多技能，而是把现有技能、数据集、sandbox、browser、memory 收敛到统一的 agent runtime 里。  
> 下一阶段一旦把持续执行、子代理、浏览器能力和安全质量闸门补齐，`Lingz` 会从“企业业务 AI 平台”升级成“真正能执行工作的企业 agent 平台”。

---

## 最终判断

和 `OpenClaw` 的差距，可以概括成一句话：

> 我们现在最缺的不是“会不会答”，而是“能不能持续、安全、可编排地把事做完”。

这也是下一阶段最值得投入的主战场。
