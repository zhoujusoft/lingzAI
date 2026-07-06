# 外部连接器迁移设计

**版本**: v1.0  
**日期**: 2026-05-26  
**状态**: 设计草案  
**适用范围**: Lingzhou Agent 外部系统连接器、自定义外部 API 工具化、身份上下文自动注入

---

## 1. 背景

本设计文档用于回答两个问题：

1. 如何把 `lcp-engine` 中 `IntegrationConnectController` 代表的连接器能力迁移到当前项目
2. 迁移时哪些能力保留，哪些能力不保留，以及自定义 API 如何支持自动绑定用户/角色等身份信息

结论先说：

> 当前项目不应直接照搬 `IntegrationConnectController` 的整套模型，而应拆成“外部连接器目录 + 认证配置 + 自定义 API 定义 + Tool 发布”四层能力。

并且本次迁移有三个明确约束：

1. **只保留外部 API 调用能力，不保留内部 API 调用能力**
2. **不做数据源 API，只做自定义外部 API**
3. **自定义 API 支持自动绑定当前调用人的身份上下文，如 userId、roleId、roleCode 等**

---

## 2. 外部项目能力拆解

`lcp-engine` 中的 `IntegrationConnectController` 实际承载了以下几类能力：

- 连接器实例管理
  - 保存连接参数
  - 保存 OAuth 配置
  - 保存 SSO 配置
- 连接器 API 管理
  - 新增、修改、删除 API
  - 测试 API
  - 直接执行 API
- 表单 / 业务对象绑定
  - API 输出参数与业务模型绑定
  - 供低代码表单运行时直接调用

这套设计是围绕原系统自己的低代码运行时、BizBus、表单模型、内部控制器路由建立的。

当前项目不是这个架构。

当前项目在本次需求中真正需要的是两类能力：

- 外部连接器与认证
- 自定义 API 目录与工具化执行
- Tool 目录与动态注册

因此迁移时不能“控制器搬家”，而必须“能力重组”。

---

## 3. 当前项目已具备的可复用能力

### 3.1 Tool 动态注册能力

当前项目已经有三套成熟模板：

- 低代码 API 工具
- MCP 工具

其中最接近本次目标的是：

- `LowcodeApiCatalogService`
- `LowcodeToolRegistryService`
- `ToolLibraryCallbackResolver`

这意味着：

> 自定义外部 API 的最佳落点是参照 `LOWCODE_API` 的实现方式，新增一类 `CONNECTOR_API` 工具。

### 3.2 当前认证上下文能力

当前项目请求链路中，登录后的 HTTP 请求已经能从拦截器拿到：

- `UserId`
- `UserCode`

它们来自 `AuthTokenInterceptor` 写入的 request attribute。

同时，系统内的用户与角色模型已经存在：

- `t_user`
  - `id`
  - `code`
  - `name`
  - `mobile`
  - `email`
  - `role_id`
- `sys_role`
  - `id`
  - `role_code`
  - `role_name`

这意味着：

> “API 自动绑定身份信息”不需要调用方手工传 `userId`、`roleId`，而应该由连接器执行层基于当前登录态自动解析并注入。

---

## 4. 迁移边界

### 4.1 保留的能力

保留以下能力：

- 外部连接器实例管理
- 自定义外部 API 定义管理
- OAuth2 / API Key / 无认证
- 请求模板渲染
- API 测试调用
- API 发布为 Tool
- 输出 JSONPath 映射
- 自动注入当前用户身份变量

### 4.2 不保留的能力

本次不保留以下能力：

- 内部 API 调用
- 基于 Spring MVC handler 的 mock 调用
- BizBus / BizService 绑定
- 表单 Schema API 绑定
- 原系统 SSO 跳转能力
- 数据源 API
- 基于表 / SQL 自动生成 API 的旧模式

一句话：

> 当前项目只接外部系统，只做自定义外部 API，不接内部控制器，也不再做数据源 API。

---

## 5. 为什么不保留内部 API 调用

原系统 `ConnectAuthService` 同时支持两类调用：

- 外部 API
- 内部 API

其中“内部 API”本质上是：

- 构造 `MockHttpServletRequest`
- 通过 Spring `HandlerMapping` 找控制器
- 直接在服务端内部转发执行

这套做法依赖原系统特定前提：

- 内部业务能力天然以 Controller 暴露
- API 配置和内部表单 / BizBus 模型是一体的
- 连接器承担了系统内外统一编排入口的职责

当前项目不适合这样做，原因有三点：

### 5.1 抽象层级不对

当前项目的核心目标是把能力收敛为 Tool，而不是把控制器当作统一执行引擎。

如果保留内部 API 调用，会形成第二套能力入口：

- 一套是 Tool Registry
- 一套是 Connector 内部路由调用

这会破坏当前项目已经形成的统一 Tool 边界。

### 5.2 安全边界不清晰

内部 API 调用一旦开放，会出现几个问题：

- 哪些 Controller 允许被配置成连接器 API
- 权限如何与当前用户、当前会话、当前 Skill 绑定
- 是否允许绕过现有 Tool 级权限控制

这会让系统出现“表面上是 Tool，实际是隐藏内部路由直通”的问题。

### 5.3 与当前运行时不一致

当前系统的对话、技能、数据集、MCP 都是通过 Tool 统一接入的。

如果连接器内部再保留一套“内部 API 执行器”，后续：

- 观测
- 权限
- 绑定
- 导入导出
- 失败处理

都会多出一套兼容逻辑。

因此本次方案明确：

> `IntegrationConnectController` 中类似 `handleInsideApi` 的能力不迁移。

---

## 6. 目标架构

建议把外部连接器能力拆成四层。

### 6.1 连接器目录层

负责管理“如何连接外部系统”。

建议新增表：

- `integration_connector`

建议字段：

- `id`
- `name`
- `alias`
- `base_url`
- `auth_type`
- `auth_config_json`
- `connect_params_json`
- `status`
- `owner_user_id`
- `permission_scope`
- `created_at`
- `updated_at`

说明：

- `auth_config_json` 保存 OAuth2、API Key 等配置
- `connect_params_json` 保存环境变量、租户号、固定 header、固定 query 等模板参数

### 6.2 自定义 API 定义层

负责管理“连接器下有哪些可调用的自定义外部 API”。

建议新增表：

- `integration_connector_api`

建议字段：

- `id`
- `connector_id`
- `api_code`
- `api_name`
- `description`
- `method`
- `path_template`
- `headers_json`
- `query_params_json`
- `body_template`
- `content_type`
- `input_schema_json`
- `output_mapping_json`
- `identity_binding_policy_json`
- `enabled`
- `tool_name`
- `created_at`
- `updated_at`

说明：

- `path_template` 支持变量替换
- `input_schema_json` 直接面向 Tool 入参定义
- `output_mapping_json` 用于把原始 JSON 提炼为稳定结构
- `identity_binding_policy_json` 用于声明哪些 header / query / body 字段需要自动注入当前调用人的身份变量

### 6.3 执行层

负责：

- 认证
- token 获取与缓存
- 请求模板渲染
- 身份变量装配
- 发起外部 HTTP 请求
- 响应解析

建议拆成两个服务：

- `ConnectorAuthService`
- `ConnectorIdentityBindingService`
- `ConnectorApiExecutor`

### 6.4 Tool 发布层

负责把 API 定义发布成当前系统可绑定、可执行的 Tool。

建议新增：

- `ConnectorApiCatalogService`
- `ConnectorToolPublishService`
- `ConnectorToolRegistryService`

并在 `tool_catalog.tool_type` 中新增：

- `CONNECTOR_API`

`tool_catalog.source` 建议形如：

- `connector:{connectorId}:{apiCode}`

---

## 7. API 自动绑定用户/角色等身份信息设计

### 7.1 需求定义

这里说的“自动绑定身份信息”，不是指把用户信息写死在连接器配置里，而是指：

- 同一个连接器 API
- 被不同登录用户调用时
- 系统自动在请求里补上当前调用人的身份参数
- 使远端系统可以基于这些参数返回不同数据

典型例子：

- header 自动带 `X-User-Id`
- header 自动带 `X-Role-Id`
- query 自动带 `userCode`
- body 自动带 `operator.roleCode`

这样就能满足：

- 不同的人调同一个 API
- 远端看到的身份上下文不同
- 返回结果也可以不同

### 7.2 设计原则

原则一：

> 身份参数由系统自动注入，不要求前端和 Tool 调用者手工传。

原则二：

> 用户显式传入的业务参数优先，系统注入的身份参数默认只补空，不强行覆盖，除非策略明确要求覆盖。

原则三：

> 身份注入只面向允许暴露的字段白名单，不能把整个用户对象原样透传到远端。

原则四：

> 连接器只负责“当前登录人”的身份上下文，不负责模拟任意用户身份。

### 7.3 身份变量来源

建议执行链路中统一解析出 `ConnectorIdentityContext`，最小字段集如下：

- `user.id`
- `user.code`
- `user.name`
- `user.mobile`
- `user.email`
- `user.userType`
- `user.state`
- `role.id`
- `role.code`
- `role.name`

可选扩展字段：

- `role.menuPermissions`
- `agent.id`
- `agent.name`

来源建议：

- `UserId`、`UserCode`：来自 `AuthTokenInterceptor`
- 用户详情：由 `UserService` 或专门的 identity resolver 查询
- 角色详情：由 `role_id -> sys_role` 查询
- 角色菜单权限：按需查询，不默认每次加载
- agent 信息：按需查询，不默认作为首批字段

### 7.4 注入目标位置

身份信息建议支持注入到三个位置：

- header
- query
- body

不建议支持：

- URL host
- method
- OAuth 凭证本体

原因：

- 这些字段会影响请求安全边界和连接器稳定性
- 不适合作为用户上下文动态变量

### 7.5 变量命名规范

建议统一暴露一套稳定变量前缀：

- `user.id`
- `user.code`
- `user.name`
- `user.mobile`
- `user.email`
- `user.userType`
- `user.state`
- `role.id`
- `role.code`
- `role.name`

在模板中可使用：

- `${user.id}`
- `${user.code}`
- `${role.id}`
- `${role.code}`
- `${role.name}`

如果未来要兼容更复杂嵌套，也可以支持 JSON body 中的目标路径声明，例如：

```json
{
  "identityBindings": [
    { "target": "header", "name": "X-User-Id", "source": "user.id" },
    { "target": "header", "name": "X-Role-Code", "source": "role.code" },
    { "target": "query", "name": "operatorCode", "source": "user.code" },
    { "target": "body", "path": "$.operator.roleId", "source": "role.id" }
  ]
}
```

### 7.6 执行时合并规则

建议按以下顺序组装请求：

1. 读取连接器固定配置
2. 读取 API 定义中的 headers/query/body 模板
3. 合并调用方显式传入的业务参数
4. 解析当前用户身份上下文
5. 按 `identity_binding_policy_json` 补充身份变量
6. 渲染最终请求
7. 发起调用

默认合并策略：

- `APPEND_IF_ABSENT`
  - 目标字段为空时补充
- `OVERWRITE`
  - 强制覆盖目标字段
- `ERROR_IF_CONFLICT`
  - 如果调用方已经传了该字段，则直接报错

建议默认使用：

- header/query：`APPEND_IF_ABSENT`
- body 中的 operator / requester 类字段：`OVERWRITE`

### 7.7 示例

场景：

- 远端 API 为“获取当前用户可见订单列表”
- 不同角色可见不同范围

连接器 API 配置：

- method: `POST`
- path: `/api/orders/query`
- headers:
  - `X-User-Id: ${user.id}`
  - `X-Role-Code: ${role.code}`
- body:

```json
{
  "operator": {
    "userId": "${user.id}",
    "userCode": "${user.code}",
    "roleId": "${role.id}",
    "roleCode": "${role.code}"
  },
  "filters": "${input.filters}"
}
```

最终效果：

- 用户 A 调用时自动注入 A 的身份
- 用户 B 调用时自动注入 B 的身份
- 二者调用的是同一个连接器 API 定义
- 远端系统因身份参数不同而返回不同数据

### 7.8 安全约束

必须限制：

- 不允许前端直接指定 `source=user.xxx` 之外的任意对象路径
- 不允许把密码、token、license 等敏感信息暴露为身份变量
- 不允许未登录请求使用身份自动注入

推荐约束：

- 若 API 配置要求身份注入，则测试调用至少区分两类模式：
  - 管理员代测：用当前管理员身份注入
  - 真实运行：由真实用户会话调用时注入
- Tool 调用日志中只记录“注入了哪些字段”，不记录敏感 header 实值

---

## 8. 自定义 API 的最终能力边界

迁移后只保留一条主线：

### 8.1 外部 HTTP 自定义 API

适用：

- SaaS 平台
- ERP / CRM / OA 外部接口
- 第三方开放平台
- 企业内部但通过网关开放的 HTTP 服务

能力形态：

- 连接器
- 自定义外部 API 定义
- 身份上下文自动注入
- 发布为 `CONNECTOR_API` Tool

一句话：

> 当前项目只做外部 HTTP 自定义 API，不再做数据源 API。

---

## 9. 建议实施顺序

### P0：自定义外部 API 最小闭环

先做：

- `integration_connector`
- `integration_connector_api`
- 外部 API 测试能力
- `CONNECTOR_API` Tool 发布
- Tool 动态注册

本阶段只支持：

- `NONE`
- `API_KEY`
- `OAUTH2_CLIENT_CREDENTIALS`

### P1：身份上下文自动注入

补充：

- `ConnectorIdentityContext`
- `identity_binding_policy_json`
- header/query/body 三类注入
- 当前用户与角色信息自动解析

目标：

- 同一 API 被不同用户调用时，自动带不同身份参数
- 远端系统可基于身份返回不同数据

### P2：输出映射与稳定结构

补充：

- `output_mapping_json`
- JSONPath 提取
- 数组 / 子对象映射

目标：

- Tool 返回结果从“原始 JSON”提升为“结构化对象”

---

## 10. 对应到当前仓库的落地建议

### 10.1 建议新增的后端目录

建议新增：

- `business/integration/domain/connector`
- `business/integration/mapper/connector`
- `business/integration/service/connector`
- `business/integration/controller/connector`
- `capability/api/connector`

### 10.2 建议复用的现有模式

优先复用：

- `LowcodeApiCatalogService`
- `LowcodeToolRegistryService`
- `McpToolRegistryService`
- `ToolLibraryCallbackResolver`

参考方式：

- 目录表注册
- 发布到 `tool_catalog`
- 通过 `findByName` 动态构造 `ToolCallback`

### 10.3 建议新增的工具类型

在 `tool_catalog.tool_type` 中新增：

- `CONNECTOR_API`

并把解析顺序加入 `ToolLibraryCallbackResolver`。

---

## 11. 最终结论

本次迁移不是把 `IntegrationConnectController` 原样迁到当前项目，而是提取其中真正有价值的部分：

- 外部认证
- 自定义外部 HTTP API 定义
- 请求执行
- 身份上下文自动注入
- 输出映射
- Tool 化发布

同时明确舍弃：

- 内部 API 调用
- 原系统 BizBus 绑定
- 原系统表单 Schema 绑定
- 数据源 API
- 基于表 / SQL 自动生成 API 的旧模式

一句话总结：

> 当前项目的连接器只做自定义外部 API，并支持自动绑定当前调用人的 `userId`、`roleId`、`roleCode` 等身份参数，让同一个 API 在不同用户上下文下返回不同数据。
