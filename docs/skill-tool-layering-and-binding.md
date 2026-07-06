# 技能工具分层与绑定现状说明

## 目的

这份文档用于说明当前技能工具体系的真实状态，重点回答以下问题：

- 哪些工具已经入库
- 哪些工具只存在于运行时
- 技能执行时到底按什么规则拿工具
- 是否存在“未绑定也能执行”的情况
- 后续是否需要保底工具
- 技能详情页应该展示哪些工具

---

## 当前结论

当前系统里的工具并不是单一来源，而是两套来源同时存在：

1. 运行时直接注册的工具
2. 发布后入库的工具

这会带来一个很关键的现象：

> 技能的真实可执行工具，并不完全等于“页面上已绑定的工具”。

尤其是 `filesystem skill`，当前默认会带上一整套全局工具。

---

## 一、当前工具来源分层

## 1. 运行时全局工具

这类工具通过代码直接注册进 `GlobalToolRegistry`，不依赖 `tool_catalog`。

典型工具：

- `readFile`
- `writeFile`
- `runPython`
- `get_render_template`
- `build_frontend_render_payload`
- `generate_frontend_render`

对应代码：

- [ClothingSkillConfig.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/app/ClothingSkillConfig.java)
- [GlobalToolRegistry.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/tool/registry/GlobalToolRegistry.java)

说明：

- 这些工具运行时可用
- 但不一定在数据库工具目录中有记录
- 前端渲染 3 个工具目前属于这一类

## 2. 技能运行时自带工具

这类工具来自 skill 本身的 `skill.getTools()`。

说明：

- 工具跟 skill 自身定义强绑定
- 可能入库，也可能不入库
- 当前执行时，这一类工具会天然可用

## 3. 发布入库工具

这类工具会写入 `tool_catalog`，并且支持在技能详情中做手工绑定。

当前已明确走入库发布链路的类型：

- `MCP_REMOTE`
- `LOWCODE_API`
- `DATASET_TOOL`
- `KNOWLEDGE_BASE_TOOL`

对应代码：

- [McpToolPublishService.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/mcp/publish/McpToolPublishService.java)
- [LowcodeApiToolPublishService.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/api/publish/LowcodeApiToolPublishService.java)
- [DatasetToolPublishService.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/tool/publish/DatasetToolPublishService.java)
- [KnowledgeBaseToolPublishService.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/tool/publish/KnowledgeBaseToolPublishService.java)

---

## 二、前端渲染工具当前是什么状态

前端渲染相关工具目前是：

- 运行时全局工具
- 未走 `tool_catalog` 入库发布
- 不需要手工绑定

这 3 个工具目前是：

- `get_render_template`
- `build_frontend_render_payload`
- `generate_frontend_render`

现状判断：

- 它们更像“系统运行时基础能力”
- 不是业务侧手工选择的资源型工具
- 现在把它们放在运行时层是合理的

建议：

- 继续视为“系统保底运行时工具”
- 不进入“手工绑定工具”选择器
- 但在文档和后台说明中要明确标注其性质

---

## 三、技能执行时是否严格按绑定控制

当前答案是：**不是**。

技能执行时的工具集合来自两部分：

1. skill 自带运行时工具
2. 手工绑定工具

关键代码：

- [SkillCatalogService.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/business/skill/service/SkillCatalogService.java)

其中核心逻辑是：

- `resolveSkillChatContext`
- `mergeToolCallbacks`

当前行为可以概括为：

> 运行时工具始终可用，手工绑定工具只是额外追加。

所以现在系统并不是“未绑定就绝不能执行”的白名单模型。

---

## 四、当前是否所有技能都会默认拿到公共工具

对于 `filesystem skill`，当前答案基本是：**会默认带全局工具**。

关键代码：

- [SkillRuntimeRegistry.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/capability/skillruntime/registry/SkillRuntimeRegistry.java)

这里会先取：

- `globalToolRegistry.getToolCallbacks()`

然后注册 filesystem skill 时，直接把这批工具作为 base tools 注入进去。

这意味着：

- 即使页面上没手工绑定
- 某些公共工具仍然可能在 skill 运行时天然可用

---

## 五、当前已有的绑定校验是什么

当前系统有一层校验，但它只发生在“保存绑定”时。

也就是说：

- 只允许绑定“允许追加绑定”的工具
- 不允许把非法工具名写入手工绑定表

关键代码：

- [SkillCatalogService.java](/Users/xiehb/workspace/lingzhou-agent/backend/src/main/java/lingzhou/agent/backend/business/skill/service/SkillCatalogService.java#L160)

这个校验的边界是：

- 它约束“你能绑定什么”
- 但它不约束“skill 原本运行时就自带了什么”

所以它不是严格的执行白名单校验。

---

## 六、当前体系存在的问题

## 1. 展示与真实执行不一致

页面上“已绑定工具”并不总等于真实可执行工具。

## 2. 工具来源不统一

有的工具来自运行时注册，有的工具来自工具库发布，管理模型不一致。

## 3. 权限边界不够清楚

尤其是公共工具，如果默认全量可用，会让 skill 的能力边界不清晰。

## 4. 导入导出和审计会变复杂

如果工具来源不统一，后续做技能包导入导出、环境迁移、问题排查时很难准确表达“这个技能到底依赖了什么”。

---

## 七、建议的目标分层

建议把技能工具分成 4 层。

## 1. 系统运行时工具

这类工具属于平台运行时基础能力：

- 通过内存注册提供
- 不要求入库
- 不参与手工绑定
- 数量必须极少

建议仅保留真正的平台级运行时能力，例如：

- `get_render_template`
- `build_frontend_render_payload`
- `generate_frontend_render`

这类工具的特点是：

- 主要服务平台前端渲染链路
- 更像框架能力，不是业务权限能力
- 默认可用是合理的

## 2. 技能运行时自带工具

这类工具由 skill 自身声明，属于 skill 的固有能力。

建议：

- 在详情页单独展示为“运行时工具”
- 与绑定工具区分来源
- 不通过绑定面板做增删

## 3. 公共绑定工具

这类工具虽然也是平台提供的通用能力，但不应该默认赋予所有技能。

典型例子：

- `readFile`
- `writeFile`
- `runPython`

建议规则：

- 这类工具可以入库
- 这类工具必须显式绑定后才能给 skill 使用
- 不能因为是“公共工具”就默认给所有 skill

一句话原则：

> 公共能力不等于默认授权。

尤其像“文件读取”这种能力，本质上是权限能力，不适合默认下发给所有技能。

## 4. 业务绑定工具

这类工具统一走绑定体系，并严格按绑定控制。

包括：

- 数据集工具
- 知识库工具
- MCP 工具
- API 工具

---

## 八、对技能详情页的建议

技能详情页建议只展示四种来源标签：

- `系统运行时`
- `运行时`
- `公共绑定`
- `追加绑定`

其中：

- 系统运行时：只展示，不允许解绑
- 运行时：只展示，不允许解绑
- 公共绑定：允许绑定和解绑
- 追加绑定：允许绑定和解绑

这样用户在页面上能直接理解：

- 哪些工具是平台运行时强制带的
- 哪些工具是 skill 自带的
- 哪些公共能力是我显式授权给 skill 的
- 哪些业务资源工具是我手工加上的

---

## 九、对后续实现的建议

## 第一阶段

先把语义讲清楚，不立刻做大改：

- 明确保底工具清单
- 在详情页展示“来源标签”
- 前端选择器只管“可追加绑定工具”
- 文档补齐“运行时工具不等于手工绑定工具”

## 第二阶段

如果要收口权限，建议逐步改成“严格绑定模型”：

- 公共工具不再默认整包注入
- 前端渲染这类系统运行时工具继续保留默认可用
- `readFile / writeFile / runPython` 这类公共能力改成显式绑定
- skill 执行时最终工具集 = 系统运行时工具 + 运行时工具 + 公共绑定工具 + 业务绑定工具

## 第三阶段

进一步统一工具目录：

- 能入库的工具尽量都入库
- 不能入库的工具明确标记为“运行时工具”
- 工具详情、技能详情、导入导出都按同一套分类体系表达

---

## 十、当前推荐结论

当前最合理的方向是：

1. 保留极小集合的系统运行时工具
2. 前端渲染 3 个工具继续作为系统运行时工具
3. 公共工具拆成“运行时工具”和“绑定工具”
4. `readFile` 这类公共权限工具必须显式绑定，不能默认给所有 skill
5. 业务能力型工具统一走绑定
6. 技能详情页要能明确区分“系统运行时 / 运行时 / 公共绑定 / 追加绑定”

一句话总结：

> 前端渲染工具适合作为系统运行时能力保留在内存注册层；公共工具需要拆分权限边界，其中 `readFile` 这类能力必须改成显式绑定，不能再默认给所有技能。
