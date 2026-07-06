# 技能包外部工具绑定状态说明

## 背景

当前技能包导入导出已经支持：
- 技能文件本体导出
- 技能目录元数据导出
- `skill_tool_binding` 中 `MANUAL` 绑定的工具名导出

但现有实现只导出工具名列表，且导入时主要按“公共工具是否存在”做恢复。  
这会导致以下几类依赖目标环境资源的工具，虽然可能出现在技能包配置里，但导入后无法稳定恢复：

- 数据集工具
- 知识库工具
- MCP 工具
- API 工具

这些工具背后依赖的是目标环境中的资源实体，而不是技能文件本身，因此不应被当作“可随技能包完整迁移的内容”。

## 结论

技能包需要区分两类内容：

### 1. 可随包迁移的内容
- 技能文件本体
- 技能目录元数据
- 技能自身的绑定声明

### 2. 不可随包迁移、只能记录引用状态的内容
- 数据集工具
- 知识库工具
- MCP 工具
- API 工具

一句话原则：

> 技能包导出“绑定声明”，不导出环境资源实体；导入时恢复“能恢复的绑定”，保留“不能恢复的状态”。

## 为什么外部工具不能直接随包导出

### 数据集工具
- 依赖目标环境中的数据集实体、发布状态和数据结构
- 不同环境中的数据集 ID、对象编码、数据源可能不一致

### 知识库工具
- 依赖目标环境中的知识库实体、文档内容和发布状态
- 技能包不应隐式复制知识库内容

### MCP 工具
- 依赖目标环境中的 MCP Server 配置、连接地址、认证方式和可用工具集
- 同名工具在不同环境下可能并不对应同一个服务

### API 工具
- 依赖目标环境中的 API 目录、工具发布状态和远端接口可访问性
- 技能包不应隐式复制 API 注册资源

## 技能包中应该保存什么

现有 `toolBindings: List<String>` 不足以表达绑定恢复状态。  
后续应升级为“带状态的绑定快照”。

建议每条绑定至少包含以下信息：

- `toolName`
- `bindingType`
- `toolSourceType`
- `exportMode`
- `referenceStatus`
- `referenceMeta`

### 字段说明

#### `toolName`
- 当前绑定的工具名
- 例如：`lowcode.11290.zdy_xiansxx`

#### `bindingType`
- 当前绑定类型
- 例如：`MANUAL`、`NATIVE`

#### `toolSourceType`
- 工具来源类型
- 建议支持：
  - `GLOBAL`
  - `MCP_REMOTE`
  - `LOWCODE_API`
  - `DATASET_TOOL`
  - `KNOWLEDGE_BASE_TOOL`

#### `exportMode`
- 表示该工具在技能包中的导出方式
- 建议支持：
  - `INLINE`
  - `REFERENCE_ONLY`

约定：
- `GLOBAL` 工具可视为 `INLINE`
- `MCP_REMOTE / LOWCODE_API / DATASET_TOOL / KNOWLEDGE_BASE_TOOL` 统一视为 `REFERENCE_ONLY`

#### `referenceStatus`
- 表示导出时该引用的状态
- 当前建议导出时固定为：`BOUND`

#### `referenceMeta`
- 引用型工具的补充信息
- 用于辅助导入时匹配目标环境资源
- 示例：
  - API 工具可记录 `platformKey`、`apiCode`
  - MCP 工具可记录 `serverId`、`serverCode`
  - 数据集工具可记录 `datasetId`、`datasetCode`
  - 知识库工具可记录 `knowledgeBaseId`、`knowledgeBaseCode`

## 推荐的技能包配置结构

```json
{
  "skillCatalog": {
    "runtimeSkillName": "intelligent-form-fill",
    "displayName": "智能填报",
    "description": "你只需要说一段话，系统会智能提取关键信息，并生成可确认的表单填写草稿。",
    "category": "表单填报",
    "visible": true,
    "sortOrder": 190
  },
  "toolBindings": [
    {
      "toolName": "lowcode.11290.zdy_xiansxx",
      "bindingType": "MANUAL",
      "toolSourceType": "LOWCODE_API",
      "exportMode": "REFERENCE_ONLY",
      "referenceStatus": "BOUND",
      "referenceMeta": {
        "platformKey": "11290",
        "apiCode": "zdy_xiansxx"
      }
    }
  ]
}
```

## 导入时必须体现的状态

导入成功不等于绑定恢复成功。  
导入结果中必须单独返回每条绑定的恢复状态。

建议的恢复状态：

- `RESTORED`
- `REFERENCE_CONFIRMED`
- `MISSING_DEPENDENCY`
- `NAME_CONFLICT`
- `UNSUPPORTED`
- `SKIPPED`
- `NEEDS_REBIND`

### 含义说明

#### `RESTORED`
- 已成功恢复绑定

#### `REFERENCE_CONFIRMED`
- 目标环境存在对应引用资源，已确认可用
- 当前不一定需要重新写入绑定

#### `MISSING_DEPENDENCY`
- 技能引用的目标资源在当前环境不存在

#### `NAME_CONFLICT`
- 找到同名对象，但类型不一致或不属于同一资源

#### `UNSUPPORTED`
- 当前版本尚不支持自动恢复该类绑定

#### `SKIPPED`
- 明确跳过恢复

#### `NEEDS_REBIND`
- 需要用户在导入后手工重新绑定

## 导入结果建议结构

```json
{
  "toolBindingResults": [
    {
      "toolName": "lowcode.11290.zdy_xiansxx",
      "toolSourceType": "LOWCODE_API",
      "restoreStatus": "RESTORED",
      "message": "已恢复低代码 API 工具绑定"
    },
    {
      "toolName": "kb.sales_policy.search",
      "toolSourceType": "KNOWLEDGE_BASE_TOOL",
      "restoreStatus": "MISSING_DEPENDENCY",
      "message": "目标环境未找到对应知识库工具"
    }
  ]
}
```

## 预览阶段必须做的事

在 `previewImport` 阶段就应明确展示：

- 技能本体是否可导入
- 绑定总数
- 引用型绑定总数
- 可恢复数量
- 缺失依赖数量
- 需要人工重绑的数量

不应等到导入完成后才让用户发现技能能力残缺。

## 执行阶段必须做的事

导入执行时按以下原则处理：

### 1. 可恢复的绑定
- 直接恢复

### 2. 引用型但目标环境缺失
- 不静默丢弃
- 必须记录恢复状态
- 必须进入导入结果 warnings 或 binding results

### 3. 当前不支持自动恢复的绑定
- 标记为 `UNSUPPORTED` 或 `NEEDS_REBIND`
- 由用户后续手工处理

## 当前版本建议的边界

第一阶段不做：
- 跨环境自动创建数据集
- 跨环境自动创建知识库
- 跨环境自动创建 MCP Server
- 跨环境自动创建低代码 API 注册资源

第一阶段只做：
- 导出绑定声明
- 导入时识别引用型绑定
- 尝试恢复已有资源绑定
- 对缺失项输出明确状态

## 最终原则

技能包负责带走：
- 技能本体
- 技能元数据
- 技能依赖声明

技能包不负责强行带走：
- 目标环境的资源实体

系统必须保证：
- 绑定是否恢复成功，对用户是可见的
- 引用型依赖是否缺失，对用户是可见的
- 导入成功但部分能力不可用，这件事不能被静默隐藏
