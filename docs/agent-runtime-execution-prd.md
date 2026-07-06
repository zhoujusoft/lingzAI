# Agent Runtime 执行主流程改造 PRD

## 1. 背景

当前通用会话链路已经接入统一 runtime，但总体执行主流程仍保留了较强的“前置技能路由”思维：

```text
UserMessage
→ Skill Router LLM
→ GENERAL / SKILL
→ Main LLM
→ Tool / Skill / Runtime
```

该方案在工程上可运行，但已经暴露出几个明显问题：

1. 普通聊天也会触发一次前置模型路由，导致双模型调用，延迟偏高。
2. 顶层先做 skill 分类，和实际执行策略之间存在错位。
3. skill 状态恢复、skill 切换、general world 与 skill world 的边界控制复杂。
4. Python 执行能力仍被当作普通工具使用，容易被模型误用。
5. 当前系统的核心矛盾已经不是“命中哪个 skill”，而是“这次请求该进入哪种执行模式”。

本 PRD 的目标，是将现有 runtime 从“技能路由优先”升级为“执行策略优先”。

---

## 2. 目标

### 2.1 产品目标

将个人 Agent / 普通聊天链路统一收敛为一套稳定的执行骨架。

注意：

1. 用户所有输入都发生在聊天界面里，但这不代表执行层都属于“无需外部能力的聊天回答”。
2. 本文讨论的是“执行模式”，不是“交互形态”。

目标骨架如下：

```text
UserMessage
→ 判断：是否可直接回答（DIRECT）
→ DIRECT：直接回复
→ 非 DIRECT：优先尝试已有能力（TOOL）
→ TOOL 无法完成：再升级到 CODE
```

### 2.2 设计目标

1. 顶层不再做前置 Skill Router LLM。
2. Skill 不再是顶层路由目标，而是 TOOL 层内部的“可加载能力包”。
3. General 会话默认支持主模型 in-band 选择能力。
4. Code 执行从“普通 tool action”提升为“重型执行模式”。
5. 保留现有 runtime、skill、workspace、artifact 体系，优先演进而非推翻。

### 2.3 非目标

本期不做：

1. 多 Agent 协同执行重构。
2. 前端可视化执行流程重做。
3. 完整自动规划器（Planner）复杂 JSON 输出协议。
4. 通用 Python AutoFix / AutoRetry 智能循环。

---

## 3. 核心问题定义

当前顶层路由核心问题不是“用户属于哪个技能领域”，而是：

```text
当前请求属于：
- 无需外部能力即可直接回答
- 已有能力执行
- 动态代码执行
```

这里需要特别避免一个常见误解：

```text
用户在聊天界面发问
!=
执行层就是 DIRECT
```

例如：

```text
“上海报销标准是什么？”
```

从用户视角看，这是自然语言问答；
但从执行层看，它通常不属于“直接回答”，而属于：

```text
TOOL
```

因为它往往需要调用报销 skill、知识库、数据集或其他外部能力。

因此，系统顶层应从：

```text
Intent -> Skill
```

升级为：

```text
Task -> Execution Strategy
```

---

## 4. 新执行模型

## 4.1 顶层模式

定义三种执行模式：

| 模式 | 含义 | 特征 |
| --- | --- | --- |
| DIRECT | 直接回答 | 不依赖外部工具/skill/数据源即可完成 |
| TOOL | 调已有能力 | 调 skill / runtime tool / dataset / MCP / KB / API |
| CODE | 动态生成能力 | 生成并执行 Python，产出文件/图表/文档等 |

说明：

1. DIRECT 是最低成本模式。
2. TOOL 是标准执行模式。
3. CODE 是最高成本、最强能力模式。

---

## 4.2 推荐主链路

```text
UserMessage
→ Task Analyzer
→ DIRECT ?
   ├─ YES → Direct LLM
   └─ NO  → TOOL Planner
            → 现有能力是否足够？
               ├─ YES → TOOL Runtime
               └─ NO  → CODE Runtime
```

这不是三个完全互斥分支，而是一条能力升级链：

```text
DIRECT
→ TOOL
→ CODE
```

核心原则：

1. 能 DIRECT 就不要进入执行态。
2. 能 TOOL 就不要 CODE。
3. CODE 只作为重型升级路径使用。

---

## 5. Skill 的新定位

Skill 不再参与顶层路由。

Skill 的新角色定义为：

```text
Skill = TOOL 模式内部的可加载能力包
```

具体表现为：

1. 顶层不再判断“是不是报销助手/合同审核/文档翻译”。
2. 顶层只判断“是不是执行型请求”。
3. 一旦进入 TOOL 模式，模型可通过以下能力自行选择 skill：
   - `listActiveSkills`
   - 系统提示中的可选技能目录
   - `loadSkillContent(skillName)`
4. skill 激活后，才暴露 skill 私有 tool / `/skill` 路径 / skill prompt 内容。

因此：

```text
Skill != Route Target
Skill == Callable Capability Bundle
```

---

## 6. CODE 模式定位

CODE 模式不是一个普通工具，而是一种独立 runtime。

### 6.1 为什么不能继续把 Python 当普通 tool

如果继续将 `run_python` 视为一个普通工具，会出现：

1. 模型把 Python 当“顺手试一下”的工具。
2. 在 skill 未激活时错误猜测脚本路径。
3. 本应使用已有 tool 的任务被过早升级到 Python。
4. 重型执行缺乏单独的状态、日志、失败归档和升级策略。

### 6.2 CODE 模式应具备的能力

1. 沙箱/工作区
2. 中间文件管理
3. artifact 产出
4. stdout/stderr 记录
5. 执行失败归档
6. 明确的进入条件
7. 独立提示词和边界规则

---

## 7. 执行阶段定义

建议把主执行流程拆成以下阶段：

### Phase 1：请求理解

输入：

1. 用户消息
2. 附件列表
3. 当前会话上下文
4. 已加载技能状态

输出：

1. 是否可直接回答
2. 是否执行型请求
3. 是否存在明显文件处理 / 数据分析 / 产物生成倾向

### Phase 2：执行策略判定

输出：

1. `DIRECT`
2. `TOOL`
3. `CODE`

本期建议：

1. `DIRECT` 判定可用轻量规则优先处理明显问候/寒暄，以及无需外部能力即可回答的简单生成型请求。
2. 只要问题依赖 skill、知识库、数据集、外部 API、附件处理或运行时能力，即使它表现为自然语言问答，也应进入 `TOOL` 执行世界。
3. 不直接大规模引入顶层 `CODE` 直达，先保守走 `TOOL -> CODE` 升级。

### Phase 3：TOOL 执行

模型在 TOOL 世界内可：

1. 直接调用通用 runtime tool
2. 调 dataset / KB / MCP / API
3. 根据可选技能目录调用 `loadSkillContent(skillName)`
4. 激活 skill 后继续使用其专属工具

### Phase 4：CODE 升级

仅当以下情况发生时进入：

1. 现有 tool 明显不够用
2. 任务本身是复杂文件处理/数据清洗/多步骤统计/复杂产物生成
3. 执行路径已经明确需要 Python
4. 系统已经明确发放 `CODE` 执行许可，而不是模型自行猜测后直接调用 `run_python`

### Phase 4.1：TOOL -> CODE 升级策略

第一版不直接开放“通用 world 任意 run_python”，而是先建立升级策略与守卫。

核心原则：

1. `run_python` 不是普通 TOOL，不允许被模型当成“先试试看”的通用动作。
2. 顶层先进入 `TOOL` world，优先尝试 skill / KB / dataset / MCP / runtime_tool 的既有能力。
3. 只有当系统判定“当前请求具备重型加工特征”，才标记为 `TOOL -> CODE` 候选。
4. 一旦成为候选，可进入 CODE 升级路径；但必须遵守固定顺序，不允许直接跳到 `run_python`。

第一版建议识别为 `TOOL -> CODE` 候选的场景：

1. Excel / CSV / 表格的复杂清洗、聚合、透视、趋势分析、图表生成
2. Word / PDF / PPT / 多文档批量转换、拆分、合并、双语对照生成
3. 批量文件处理、复杂中间文件编排、复杂产物导出
4. 明显需要多步数据加工、图形绘制或自定义脚本逻辑的任务

第一版明确禁止升级到 CODE 的场景：

1. 纯知识问答、制度问答、报销标准查询、政策解释
2. 当前问题本质上缺的是依据，不是能力
3. 已有 skill / KB / dataset / API / MCP 明显可以覆盖
4. 只有附件读取需求，但尚未进入复杂加工
5. 模型只是因为找不到 skill 脚本，就试图随手执行一个默认 Python

第一版后端落地方式：

1. 引入 `ToolToCodeEscalationPolicy`
2. 在 `prepared.paramsJson` 中写入 `toolToCodeDecision`
3. 记录中文调试日志：
   - `recommendedPath`
   - `codeEscalationCandidate`
   - `allowCodeExecution`
   - `reason`
   - `signals`
   - `blockers`
4. `RuntimeSystemToolProvider` 不允许“上来直接 `run_python`”式执行
5. CODE 升级必须遵守固定顺序：
   - 先分析现有 skill/tool 是否足够
   - 再 `file_write` 写出 `/workspace/*.py`
   - 再 `run_python`
6. skill world 仍可沿用 skill 私有脚本执行链路

这意味着：

```text
当前阶段：
TOOL -> CODE 的边界已建立
CODE 升级路径已开放
但执行顺序必须被严格约束
```

后续第二阶段再建设真正独立的 `CODE Runtime`：

1. 独立 prompt
2. 独立状态机
3. 独立 artifact/日志/失败归档
4. 独立重试与校验策略

### Phase 5：结果输出与归档

输出：

1. 文本答案
2. 结构化 tool 结果
3. artifact
4. 错误消息与失败归档

---

## 8. 交互原则

### 8.1 DIRECT

要求：

1. 直接回复
2. 不进入 tool-aware pipeline
3. 不加载 skill
4. 最低延迟

示例：

1. 你好
2. 帮我写个周报开头
3. 解释一下 JVM

非示例：

1. 上海报销标准是什么
2. 帮我查一下某客户情况

以上虽然是自然语言问答，但通常依赖外部能力，不应归入 DIRECT。

### 8.2 TOOL

要求：

1. 主模型自己决定是否查看/加载 skill
2. 不做前置 skill router
3. 先尝试已有能力

示例：

1. 查询上海报销标准
2. 帮我审核这个合同
3. 翻译这个文档
4. 查询客户资料

### 8.3 CODE

要求：

1. 只在 TOOL 不足时升级
2. 严格限制脚本路径和工作区
3. 产物必须走 artifact 输出
4. 第一版默认不向 general world 直接放开 `run_python`
5. skill world 下的既有脚本执行不等于通用 CODE Runtime 已开放

示例：

1. 汇总 Excel 并生成趋势图
2. 批量清洗表格
3. 复杂文档转换
4. 生成定制 HTML / Word / PPT

---

## 9. 边界规则

### 9.1 General / Tool World

未激活 skill 时：

1. 不允许感知 `/skill` 内部资源。
2. 不允许假设 skill 私有工具已存在。
3. 不允许猜测 `/skill/scripts/*`。

### 9.2 Skill World

skill 已激活后：

1. 才允许注入 skill 内容。
2. 才允许访问 skill 专属工具。
3. 才允许解析 `/skill/...` 路径。

### 9.3 CODE World

进入 CODE 后：

1. 不代表可以任意访问宿主机路径。
2. 仅允许 runtime 工作区内文件。
3. 最终产物必须通过 `write_artifact` 输出。

---

## 10. 顶层系统提示词分层设计

第一版不能把顶层系统提示词做得过空。

原因：

1. 如果主模型不知道当前有哪些 tool / skill / dataset / KB / MCP 能力，就无法稳定做 in-band capability selection。
2. 如果顶层完全不写 skill 能力目录，模型即使看到了 `loadSkillContent` 这个工具名，也不知道有哪些 skill 值得加载。
3. 但如果一开始就把所有 skill 全文、所有脚本路径、所有执行细节全部注入，又会导致 prompt 过重、能力混淆和上下文污染。

因此，第一版应采用：

```text
顶层能力目录 + 激活后全文注入
```

的分层策略。

### 10.1 顶层系统提示词必须包含的内容

顶层系统提示词应固定包含以下四类信息：

#### A. 执行模式说明

告诉模型：

1. 先判断当前问题是否可直接回答。
2. 若不能直接回答，优先使用已有能力。
3. 只有已有能力不足时，才升级到 CODE。

#### B. 能力目录摘要

需要让模型知道“系统里有什么能力”，但只放摘要，不放全文细节。

建议包含：

1. 通用 runtime tool 摘要
   - `runtime_tool`
   - `parse_file`
   - `write_artifact`
2. 业务能力摘要
   - dataset
   - knowledge base
   - MCP
   - 低代码 API
3. skill 目录摘要
   - `runtimeSkillName`
   - `displayName`
   - 一句话用途
   - 典型适用场景

#### C. 调用规则

需要明确告诉模型：

1. 若问题明显命中某个 skill 领域，先调用 `loadSkillContent(skillName)`。
2. `listActiveSkills` 只代表当前已加载 skill，不代表用户全部可用能力。
3. skill 未激活前，不得假设 skill 私有工具或 `/skill` 路径已可用。

#### D. 升级规则

需要明确告诉模型：

1. 能 DIRECT 就不要进入 TOOL。
2. 能 TOOL 就不要进入 CODE。
3. 文件处理、复杂统计、批量转换、复杂产物生成等任务，才考虑升级到 CODE。

### 10.2 顶层系统提示词不应包含的内容

第一版顶层提示词不应直接塞入：

1. 所有 skill 的完整 system prompt
2. skill 私有脚本完整调用样例
3. `/skill/scripts/...` 等具体路径细节
4. 所有 reference 全文
5. 大段 skill 操作说明

这些内容应延迟到 skill 激活后再注入。

### 10.3 Skill 激活后的二段注入

当模型调用：

```text
loadSkillContent(skillName)
```

之后，才允许注入该 skill 的完整上下文，包括：

1. skill 全量说明
2. skill 私有工具说明
3. skill 参考资料
4. skill 固定脚本路径
5. skill 专属执行流程

这样形成两层结构：

```text
顶层 prompt
= 知道“有哪些能力、何时该用、边界是什么”

skill prompt
= 知道“这个能力具体怎么执行”
```

### 10.4 第一版推荐实现方式

建议保留并继续强化现有这类结构：

1. 顶层 `General / Tool World` 中包含可选 skill 目录摘要
2. 通过 `loadSkillContent(skillName)` 做按需激活
3. 激活后再注入 skill 内容与专属工具
4. 未激活 skill 时，严格禁止访问 skill 私有内部资源

这意味着第一版不是：

```text
完全依赖 tool schema 自行感知能力
```

而是：

```text
系统提示词先给能力地图
模型再按地图自主选择能力
```

这是第一版最稳的实现策略。

---

## 11. 提示词分层与现有类映射

为了避免后续实现时再次把“能力目录”和“skill 全文”混在一起，第一版需要明确把现有类职责分开。

### 11.1 顶层能力目录由谁负责

建议继续由：

```text
ChatRuntimePreparedRequestAssembler
```

负责生成顶层场景 prompt。

在第一版里，这个类应负责构造：

1. 顶层执行原则
   - DIRECT / TOOL / CODE
   - 能 DIRECT 不 TOOL
   - 能 TOOL 不 CODE
2. 可选 skill 目录摘要
   - `runtimeSkillName`
   - `displayName`
   - 一句话描述
3. 通用能力目录摘要
   - `runtime_tool`
   - `parse_file`
   - dataset / KB / MCP / API
4. skill 激活前边界规则
   - 未激活 skill 不得访问 `/skill`
   - 若命中 skill 领域，应先 `loadSkillContent`

换句话说：

```text
ChatRuntimePreparedRequestAssembler
负责给主模型一张“能力地图”
```

而不是给它所有 skill 的完整执行手册。

### 11.2 skill 全文由谁负责

建议继续由：

```text
RequestScopedSkillRuntimeService
```

负责 skill 激活后的完整注入。

这个类在第一版中的职责应明确为：

1. 注册 request-scoped skill
2. 暴露：
   - `loadSkillContent(skillName)`
   - `listActiveSkills()`
   - `loadSkillReference(...)`
3. skill 激活后返回完整 skill content
4. 管理当前激活技能集合
5. 维护 `currentRuntimeSkillName`

换句话说：

```text
RequestScopedSkillRuntimeService
负责“按需展开能力全文”
```

### 11.3 prompt 拼装由谁负责

建议继续由：

```text
PromptEngineeringService
```

负责最终 prompt 分层拼装。

建议拼装顺序为：

1. 模型默认 system prompt
2. 顶层执行模式 prompt
3. 顶层能力目录 prompt
4. 场景级 prompt
5. 已激活 skill 内容
6. request 级追加 prompt

其中要明确：

1. 顶层能力目录始终可见
2. skill 全文只有在激活后才可见
3. 未激活 skill 时，`resolveActiveSkillContent(...)` 不应注入其全文

### 11.4 tool schema 负责什么

tool schema 仍然重要，但它的职责不是替代能力目录。

tool schema 负责：

1. 告诉模型某个工具怎么调用
2. 约束参数结构
3. 给出单个工具的局部语义

顶层能力目录负责：

1. 告诉模型系统里有哪些能力类别
2. 什么时候该调用哪类能力
3. skill 和 tool 的使用边界

因此，第一版不应采取：

```text
只靠 tool schema，不给顶层能力目录
```

而应采取：

```text
能力目录负责“选路”
tool schema 负责“落调用”
```

### 11.5 第一版建议改造边界

为了控制改造风险，第一版建议只做以下 prompt 层改造：

1. 去掉 General 会话前置 skill router prompt
2. 保留并强化顶层 skill/tool 能力目录 prompt
3. 保留 request-scoped skill 激活与全文注入
4. 不在第一版重做 skill 私有 prompt 存储模型
5. 不在第一版重做 tool schema 协议

这样可以保证：

1. 模型先知道“有什么能力”
2. 再自己选择“是否加载某个 skill”
3. skill 激活后再知道“具体怎么执行”

---

## 12. 运行时状态机建议

建议新增或显式化以下运行状态：

| 状态 | 含义 |
| --- | --- |
| READY | 待执行 |
| DIRECT_RUNNING | 直接回答生成中 |
| TOOL_RUNNING | 工具执行中 |
| CODE_RUNNING | 代码执行中 |
| WAITING_CONFIRM | 等待用户确认 |
| SUCCEEDED | 执行成功 |
| FAILED | 执行失败 |
| BLOCKED | 被安全/权限/前置条件阻塞 |

说明：

1. 本期可先只做日志和事件流层的状态显式化。
2. JSON 存储结构可后续演进。

---

## 13. 事件流要求

需要持久化的关键事件：

1. 请求进入
2. 执行模式判定完成
3. skill 激活
4. 工具调用开始/完成
5. code 执行开始/完成
6. artifact 产出
7. 异常终止
8. 用户确认阻塞

特别要求：

1. token 超限
2. 模型异常
3. tool 异常
4. code 异常

以上都必须形成可持久化错误记录，不能只在界面临时展示。

---

## 14. 日志要求

日志重点不再是“命中了哪个 skill”，而是：

1. 当前执行模式是什么
2. 为什么进入该模式
3. 是否升级到更重能力
4. 当前激活技能集合
5. 当前是否进入 code runtime

建议日志字段：

1. `executionMode`
2. `modeSource`
3. `toolAwareSignal`
4. `codeEscalated`
5. `currentRuntimeSkillName`
6. `activatedSkills`
7. `artifactCount`

---

## 15. 兼容性策略

本次改造不建议一次性推翻现有 runtime。

建议兼容策略：

### 第 1 阶段

1. 去掉 General 会话前置 `SkillIntentRoutingService.route()`。
2. 保留现有 `loadSkillContent` / `listActiveSkills` / request-scoped skill runtime。
3. 保留当前 skill world 边界约束。
4. 对明显问候、寒暄和无需外部能力的问题引入轻量 `DIRECT_FAST` 判定。

### 第 2 阶段

1. 将 General 执行型请求默认切入 tool-aware pipeline。
2. skill 仅在模型主动加载时激活。
3. 从“routeDecision 驱动 skill 切换”迁移到“skill activation 驱动上下文切换”。

### 第 3 阶段

1. 独立建设 CODE runtime 提示词和状态机。
2. 将 Python 执行从普通 `runtime_tool` 语义中抽离为重型模式。

---

## 16. 第一阶段实现清单

本节仅面向“从原始稳定 runtime 起步”的第一阶段落地，不追求一次性完成全部重构，而是先把主流程从“前置 skill router”切到“主模型 in-band 选择能力”。

### 16.1 第一阶段目标

第一阶段只解决三个核心问题：

1. 普通问候不再触发双模型调用。
2. General 执行型请求不再依赖前置 `SkillIntentRoutingService.route()`。
3. skill 选择回到主模型执行链路内部，通过能力目录 + `loadSkillContent` 完成。

### 16.2 第一阶段不做的事

第一阶段明确不做：

1. 不完整重命名全部运行时 profile 枚举。
2. 不立刻建设完整独立 `CODE Runtime`。
3. 不重做现有 tool schema 协议。
4. 不大改前端交互。

### 16.3 按类改造顺序

建议按以下顺序改造。

#### Step 1：先改 `ChatRuntimePreparedRequestAssembler`

目标：

1. 去掉 General 会话中的前置 `SkillIntentRoutingService.route()` 调用。
2. 保留 skill 目录摘要 prompt。
3. 保留 mentioned skill 直入能力。
4. 在 `paramsJson` 中写入第一版执行模式信号，例如：
   - `executionModeHint=DIRECT`
   - `executionModeHint=TOOL`

建议动作：

1. 删除 General 构造流程里的 `routeDecision` 依赖。
2. 将 `buildGeneralSystemPrompt(...)` 改为只接收：
   - 用户 agent prompt
   - 顶层 skill 目录摘要
   - 顶层执行规则
3. 增加一个轻量 `DIRECT_FAST` 判定：
   - 问候
   - 寒暄
   - 极短简单闲聊
4. 非 `DIRECT_FAST` 的 General 请求，默认标记为执行型，进入 tool-aware world。

#### Step 2：再改 `AgentRuntimeProfileResolver`

目标：

1. 让 General 会话在没有前置 skill router 的情况下，也能进入 tool-aware pipeline。
2. 将画像判断重心从 `routeDecision` 改为 `executionModeHint`。

建议动作：

1. 优先读取 `paramsJson.executionModeHint`。
2. 当 `executionModeHint=DIRECT` 时：
   - profile 仍可保留 `GENERAL_CHAT`
   - pipeline = `GENERAL`
3. 当 `executionModeHint=TOOL` 时：
   - profile 可先复用现有 `SKILL_CHAT` 或新增中间 profile
   - pipeline = `TOOL_AWARE`
4. 降低 `routeDecision` 的优先级，仅用于兼容旧链路。

#### Step 3：保留 `RequestScopedSkillRuntimeService` 主体不动

目标：

1. 不重写 skill runtime。
2. 继续沿用：
   - `loadSkillContent`
   - `listActiveSkills`
   - skill 激活后的全文注入

建议动作：

1. 保持 request-scoped skill 注册逻辑不变。
2. 保持 skill 激活后设置 `currentRuntimeSkillName` 逻辑不变。
3. 仅逐步弱化对 `routeDecision` 的依赖。

#### Step 4：改 `PromptEngineeringService`

目标：

1. 明确分层拼装顶层 prompt 与 skill prompt。
2. General 执行型请求也能拿到顶层能力目录。

建议动作：

1. 顶层 prompt 固定包含：
   - 执行模式原则
   - skill 目录摘要
   - tool 能力摘要
   - 升级规则
2. skill 激活后，继续通过 `resolveActiveSkillContent(...)` 注入 skill 全文。
3. 未激活 skill 时，不注入 skill 全文。

#### Step 5：改 `ChatRuntimeExecutor`

目标：

1. 去掉“General 依赖 routeDecision 才切技能”的恢复思路。
2. 把技能恢复重心转回“历史已激活 skill 状态”。

建议动作：

1. 保留从历史消息恢复 `loadedSkills` 的机制。
2. 保留请求内显式 `mentionedSkill` 优先机制。
3. 将 `resolveRoutedRuntimeSkillName(...)` 从主路径降为兼容逻辑。

#### Step 6：日志与观测补齐

目标：

1. 第一阶段先把“模式判定”和“是否进入 tool-aware”打透。
2. 不再把重点放在“命中了哪个 skill”。

建议新增日志：

1. `executionModeHint`
2. `directFastMatched`
3. `usesToolAwarePipeline`
4. `activatedSkills`
5. `currentRuntimeSkillName`

### 16.4 第一阶段验收用例

建议至少验证以下用例：

1. `你好`
   - 命中 `DIRECT_FAST`
   - 只调用一次主模型
   - 不进入 tool-aware pipeline

2. `上海报销标准是什么`
   - 不走前置 router
   - 直接进入 tool-aware pipeline
   - 模型可自行选择 `loadSkillContent("expense-assistant")`

3. `帮我审核这个合同`
   - 进入 tool-aware pipeline
   - 模型可自行加载 `contract-review-pro`

4. `帮我统计这个 Excel 每个月销售趋势并生成图表`
   - 第一阶段可先进入 TOOL world
   - 若已有能力不足，可保留后续升级 CODE 的空间

### 16.5 第一阶段完成标志

满足以下条件即可视为第一阶段完成：

1. General 会话中已不存在前置 `SkillIntentRoutingService.route()` 主路径依赖。
2. 顶层能力目录仍存在。
3. skill 激活后全文注入仍正常。
4. 普通问候不再触发双模型调用。
5. 执行型问答可通过主模型自主加载 skill。

---

## 17. 代码改造任务单

本节用于直接指导从原始稳定 runtime 开始实施第一阶段改造。内容以文件 / 方法级 checklist 为主。

### 17.1 `ChatRuntimePreparedRequestAssembler`

文件：

```text
backend/src/main/java/lingzhou/agent/backend/business/chat/runtime/ChatRuntimePreparedRequestAssembler.java
```

目标：

1. 去掉 General 会话里的前置 `SkillIntentRoutingService.route()` 主路径依赖。
2. 保留顶层 skill / tool 能力目录。
3. 增加轻量 `DIRECT_FAST` 判定与 `executionModeHint` 输出。

建议任务：

- [ ] 删除 `buildGeneral(...)` 中对 `skillIntentRoutingService.route(...)` 的调用。
- [ ] 删除 `buildGeneral(...)` 中基于 `routeDecision` 的 params 写入逻辑。
- [ ] 在 `buildGeneral(...)` 中新增本地轻量判定方法，例如：
  - `resolveExecutionModeHint(...)`
  - `matchesDirectFast(...)`
- [ ] 在 `paramsJson` 中增加：
  - `executionModeHint`
  - `directFastMatched`
- [ ] 保留 `mentionedSkill` 直入逻辑：
  - `resolveMentionedSkill(...)`
  - `buildMentionedLoadedSkills(...)`
- [ ] 将 `buildGeneralSystemPrompt(...)` 改为不再依赖 `routeDecision`。
- [ ] 保留 `buildAvailableSkillsPrompt(...)`，但明确它是“能力目录摘要”，不是 skill 全文。

建议新增/调整的方法：

1. `private String resolveExecutionModeHint(...)`
2. `private boolean matchesDirectFast(...)`
3. `private String buildGeneralExecutionPolicyPrompt(...)`

建议删除或降级的方法：

1. `buildSkillIntentPrompt(...)`
2. `localizeSkillIntentSource(...)`

### 17.2 `ChatRuntimePreparedRequest`

文件：

```text
backend/src/main/java/lingzhou/agent/backend/business/chat/runtime/ChatRuntimePreparedRequest.java
```

目标：

1. 第一阶段尽量不改 record 结构。
2. 优先通过 `paramsJson` 承载执行模式信号，避免大范围联动改造。

建议任务：

- [ ] 第一阶段不强制新增字段。
- [ ] 保持：
  - `withParamsJson(...)`
  - `withSkillState(...)`
  可继续使用。
- [ ] 如后续要提升类型安全，再考虑第二阶段新增显式字段：
  - `executionModeHint`
  - `directFastMatched`

### 17.3 `AgentRuntimeProfileResolver`

文件：

```text
backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/AgentRuntimeProfileResolver.java
```

目标：

1. 用 `executionModeHint` 代替 `routeDecision` 成为 General 会话画像主信号。
2. 让 General 执行型请求直接进入 tool-aware pipeline。

建议任务：

- [ ] 新增 `resolveExecutionModeHint(...)`，从 `paramsJson` 读取模式信号。
- [ ] 在 `resolve(...)` 日志中增加：
  - `executionModeHint`
  - `directFastMatched`
- [ ] 当 `executionModeHint=DIRECT` 时：
  - `profile = GENERAL_CHAT`
  - `pipeline = GENERAL`
- [ ] 当 `executionModeHint=TOOL` 时：
  - 第一阶段可复用 `SKILL_CHAT` profile
  - `pipeline = TOOL_AWARE`
- [ ] 将 `hasToolAwareRuntimeSignal(...)` 的优先级改为：
  1. `executionModeHint`
  2. `runtimeSkillName`
  3. `routeDecision`（仅兼容）
- [ ] 保留 `resolveRouteDecision(...)`，但仅用于旧链路兼容和过渡日志。

建议新增的方法：

1. `private String resolveExecutionModeHint(ChatRuntimePreparedRequest prepared)`
2. `private boolean isDirectMode(ChatRuntimePreparedRequest prepared)`
3. `private boolean isToolMode(ChatRuntimePreparedRequest prepared)`

### 17.4 `PromptEngineeringService`

文件：

```text
backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/prompt/PromptEngineeringService.java
```

目标：

1. 明确顶层 prompt 与 skill prompt 的分层。
2. General 执行型请求进入 tool-aware 后，仍只看到能力目录摘要，不看到未激活 skill 全文。

建议任务：

- [ ] 检查 `resolvePromptPack(...)` 当前拼装顺序，确保：
  1. 基础 system prompt
  2. 顶层执行策略 prompt
  3. 顶层能力目录 prompt
  4. 场景 prompt
  5. 已激活 skill 内容
  6. request 追加 prompt
- [ ] 保持 `resolveActiveSkillContent(...)` 只注入已激活 skill。
- [ ] 保持 `allowSkillInternals(...)` 边界控制。
- [ ] 若现有 `prepared.systemPrompt()` 过于混杂，可拆分：
  - 顶层执行规则
  - 顶层能力目录
  - 场景附加块

建议新增/关注的方法：

1. `resolvePromptPack(...)`
2. `resolveActiveSkillContent(...)`
3. `allowSkillInternals(...)`

### 17.5 `RequestScopedSkillRuntimeService`

文件：

```text
backend/src/main/java/lingzhou/agent/backend/business/chat/runtime/RequestScopedSkillRuntimeService.java
```

目标：

1. 第一阶段尽量不改主体。
2. 保留 request-scoped skill 激活与全文注入闭环。

建议任务：

- [ ] 保持 `buildSkillKit(...)` 主体不动。
- [ ] 保持以下工具不动：
  - `loadSkillContent(...)`
  - `listActiveSkills()`
  - `loadSkillReference(...)`
- [ ] 保持 `currentRuntimeSkillName` 跟踪逻辑不动。
- [ ] 将 `resolveRoutedRuntimeSkillName(...)` 标记为兼容逻辑，后续逐步降级。

后续可选任务：

- [ ] 第二阶段移除 `PARAM_ROUTE_DECISION` 依赖。

### 17.6 `ChatRuntimeExecutor`

文件：

```text
backend/src/main/java/lingzhou/agent/backend/business/chat/service/ChatRuntimeExecutor.java
```

目标：

1. 让 skill 状态恢复不再依赖“本轮 router 决策”。
2. 恢复逻辑重新聚焦：
   - 请求内显式 skill
   - 历史已激活 skill

建议任务：

- [ ] 调整 `restoreLoadedSkills(...)`：
  - 保留请求内 `loadedSkills`
  - 保留历史消息 skill 恢复
  - 降低 `resolveRoutedRuntimeSkillName(...)` 主路径作用
- [ ] 若 General 已通过 `executionModeHint=TOOL` 进入执行态，不应再要求存在 `routeDecision`
- [ ] 保留失败归档逻辑不动

重点方法：

1. `restoreLoadedSkills(...)`
2. `persistPreRuntimeFailure(...)`

### 17.7 `SkillIntentRoutingService`

文件：

```text
backend/src/main/java/lingzhou/agent/backend/business/chat/runtime/SkillIntentRoutingService.java
```

目标：

1. 第一阶段退出 General 主路径。
2. 先不急着物理删除，保留为兼容 / 实验实现。

建议任务：

- [ ] 从 `ChatRuntimePreparedRequestAssembler.buildGeneral(...)` 中移除调用。
- [ ] 保留类文件一段时间，避免影响其他分支或实验逻辑。
- [ ] 在文档和日志中将其标记为“旧路由链路”。

### 17.8 `ObservabilityCapabilityAdapter` 与日志

文件：

```text
backend/src/main/java/lingzhou/agent/backend/capability/agentruntime/capabilities/ObservabilityCapabilityAdapter.java
```

以及相关 runtime 日志输出点。

目标：

1. 让第一阶段能清楚观察 DIRECT / TOOL 的切换。
2. 把观测重点从 `routeDecision` 转到 `executionModeHint`。

建议任务：

- [ ] 新增日志字段：
  - `executionModeHint`
  - `directFastMatched`
  - `usesToolAwarePipeline`
  - `activatedSkills`
  - `currentRuntimeSkillName`
- [ ] 保留 `routeDecision` 日志，但降级为兼容信息。
- [ ] General 问候测试时，日志中应清晰看到：
  - `executionModeHint=DIRECT`
  - `usesToolAwarePipeline=false`
- [ ] 执行型问答测试时，日志中应清晰看到：
  - `executionModeHint=TOOL`
  - `usesToolAwarePipeline=true`

### 17.9 `application.yml` 日志范围

文件：

```text
backend/src/main/resources/application.yml
```

建议任务：

- [ ] 第一阶段调试期间至少打开以下包的 `DEBUG`：
  - `lingzhou.agent.backend.capability.agentruntime`
  - `lingzhou.agent.backend.business.chat.runtime`
  - `lingzhou.agent.backend.business.chat.service`
- [ ] 若需要定位模型装配与调用，再额外打开：
  - `lingzhou.agent.backend.capability.modelruntime`

### 17.10 第一阶段实施顺序建议

建议实际施工顺序：

1. 先改 `ChatRuntimePreparedRequestAssembler`
2. 再改 `AgentRuntimeProfileResolver`
3. 再改 `PromptEngineeringService`
4. 再调 `ChatRuntimeExecutor`
5. 最后补日志与回归验证

原因：

1. 先把请求组装阶段从前置 router 脱开。
2. 再让画像层读新的模式信号。
3. 再确认 prompt 仍能正确给能力地图。
4. 最后再修 skill 恢复和日志观测。

### 17.11 第一阶段测试回归清单

第一阶段改造完成后，至少需要覆盖以下回归项。

#### A. DIRECT 路径

- [ ] 输入 `你好`
  - 仅一次主模型调用
  - `executionModeHint=DIRECT`
  - `usesToolAwarePipeline=false`
  - 不触发 `loadSkillContent`
  - 不触发 `runtime_tool`

- [ ] 输入 `帮我写一段周报开头`
  - 命中 DIRECT
  - 不进入 tool-aware pipeline
  - 能正常直接生成文本

#### B. TOOL 路径

- [ ] 输入 `上海报销标准是什么`
  - 不经过前置 `SkillIntentRoutingService.route()`
  - `executionModeHint=TOOL`
  - `usesToolAwarePipeline=true`
  - 模型可自行调用 `loadSkillContent("expense-assistant")`

- [ ] 输入 `帮我审核这个合同`
  - 能主动加载 `contract-review-pro`
  - skill 激活后能看到 skill 全文和 skill tools

- [ ] 输入 `把这个英文文档翻译成中文`
  - 能主动加载 `ai-doc-translation`
  - 未激活前不访问 `/skill`
  - 激活后才允许运行 skill world 能力

#### C. skill 状态延续

- [ ] 先执行一轮报销问题，激活 `expense-assistant`
- [ ] 再追问 `那上海呢`
  - 能恢复历史 skill 状态
  - 不要求前置 router

- [ ] 已激活翻译 skill 后，再问报销问题
  - 不应被旧 skill 锁死
  - 应允许主模型切换到其他 skill

#### D. 边界约束

- [ ] 未激活 skill 时，不能访问 `/skill/scripts/...`
- [ ] 未确认脚本路径时，不能猜测 `/workspace/tools/*`
- [ ] skill 未激活时，不应注入其完整 prompt

#### E. 异常与失败归档

- [ ] token 超限时有持久化失败记录
- [ ] tool 异常时有持久化失败记录
- [ ] Python 执行异常时有持久化失败记录
- [ ] 刷新页面后仍能看到失败事件或失败消息

### 17.12 第一阶段回滚与提交策略

为了避免改造过程中把原始稳定链路拖坏，建议按以下策略实施。

#### A. 起点策略

- [ ] 从“原始稳定 runtime”开一条新的改造线
- [ ] 当前实验性改动只作为参考，不直接继续叠加

#### B. 提交粒度

建议按以下粒度拆提交：

1. 提交 1：
   - 文档与日志字段准备
   - 不改主执行逻辑

2. 提交 2：
   - `ChatRuntimePreparedRequestAssembler`
   - `executionModeHint`
   - `DIRECT_FAST`

3. 提交 3：
   - `AgentRuntimeProfileResolver`
   - General -> TOOL_AWARE 切换

4. 提交 4：
   - `PromptEngineeringService`
   - prompt 分层整理

5. 提交 5：
   - `ChatRuntimeExecutor`
   - skill 恢复兼容整理

#### C. 回滚策略

- [ ] 保留 `SkillIntentRoutingService` 文件一段时间，不先物理删除
- [ ] 保留 `routeDecision` 兼容读取逻辑，不立即彻底移除
- [ ] 若新链路出现大面积误判，可临时回退到：
  - DIRECT 走原链路
  - TOOL 仍走旧 tool-aware 逻辑

#### D. 风险控制点

第一阶段最容易出问题的点有：

1. General 执行型请求没有正确进入 tool-aware pipeline
2. 顶层 prompt 丢失 skill 目录，导致模型不知道能加载什么
3. 历史 skill 恢复被破坏
4. 问候误判为 TOOL，重新回到高延迟
5. 执行型问答误判为 DIRECT，导致不查知识库/skill 直接编造

因此每次提交后都应优先回归这五类问题。

---

## 18. 对现有代码的改造建议

### 18.1 建议保留

1. `RequestScopedSkillRuntimeService`
2. `loadSkillContent / listActiveSkills`
3. runtime workspace / artifact 体系
4. 失败归档与事件流持久化
5. skill world 的 `/skill` 边界约束

### 18.2 建议降级或移除

1. `SkillIntentRoutingService` 前置路由职责
2. `routeDecision` 对 General 会话画像的主导地位
3. 顶层 `GENERAL / SKILL` 作为主要执行抽象

### 18.3 建议新增

1. `ExecutionMode` 抽象
2. `ExecutionModeResolver`
3. `ToolEscalationPolicy`
4. `CodeExecutionRuntime` 或等价服务抽象

---

## 19. 成功标准

改造成功后，应满足：

1. 用户发送“你好”时，不再触发双模型调用。
2. 普通执行型请求不依赖前置 Skill Router，也能正确加载 skill 并执行。
3. 未激活 skill 时，模型不会访问 `/skill`。
4. 复杂文件/数据任务可从 TOOL 升级到 CODE。
5. 执行失败能够被持久化记录，而不是只在页面瞬时展示。

---

## 20. 明确结论

本项目执行主流程的正确方向应为：

```text
顶层先判执行模式
而不是先判技能
```

即：

```text
DIRECT
→ TOOL
→ CODE
```

而不是：

```text
GENERAL
→ SKILL
```

Skill 在新架构中的角色是：

```text
TOOL 模式中的动态能力包
```

不是顶层路由目标。
