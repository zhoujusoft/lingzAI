# 会话级 SSE 事件流演进草案

**版本**: v0.2  
**日期**: 2026-04-21  
**状态**: 设计草案，暂不实施  
**适用范围**: 当前聊天 / 技能 / 数据集 / 渠道链路，后续个人助理 agent 改造优先参考

---

## 1. 背景

当前聊天主链路采用的是“单次请求级 SSE”模式：

1. 前端发起一次聊天请求
2. 后端创建一次流式响应
3. 在这次响应中返回：
   - `meta`
   - `message`
   - `tool`
   - `result`
   - `done`
4. 请求结束后，连接关闭

这套模式适合：

1. 普通问答
2. 一问一答型技能
3. 工具数量较少、链路较短的技能会话

但当系统逐步向“个人助理 agent”演进时，当前模式会暴露出明显瓶颈。

---

## 2. 当前问题

## 2.1 事件顺序难以稳定表达

当前技能链路中，模型真实运行过程往往是：

1. 内容
2. 工具调用
3. 内容
4. 工具调用
5. 内容

但实际返回链路里，文本流和工具流是并行合并的，因此前端最终更容易展示成：

1. 工具调用
2. 工具调用
3. 最终内容

根因不是单点 bug，而是通信协议本身没有把“交错事件顺序”作为一等事实保留下来。

---

## 2.2 当前链路更像“回复流”，不是“事件流”

当前 SSE 本质上还是：

1. 用户发一句
2. 系统尽量流式返回一个答案

即使中间插入工具调用，也依然围绕“最终回复”组织。

但个人助理 agent 的真实过程通常不是这样，而是：

1. 接收用户请求
2. 路由到技能 / 模式
3. 调用多个工具
4. 产生中间状态
5. 可能等待人工确认
6. 继续执行
7. 异步返回结果
8. 在会话中追加后续事件

也就是说，后续主模型应该是：

> 一条用户输入，对应多条执行事件，而不是一条最终回复。

---

## 2.3 请求级 SSE 不适合异步延续

当前连接随着本次请求完成而结束。

这会限制以下能力：

1. 异步长任务完成后继续推送结果
2. 渠道消息回执、投递结果、重试结果的统一回推
3. 人工确认之后在原会话中恢复执行
4. agent 后续补充回答或持续观察型能力

---

## 2.4 展示层与运行层语义混在一起

当前前端与后端都仍然带有明显的“一问一答”结构假设：

1. 用户消息是一条
2. 助手回复是一条
3. 工具事件被当作回复的附属片段

但后续 agent 形态更合理的结构应该是：

1. 顶层会话消息层：面向用户展示
2. 底层运行事件层：面向 agent 生命周期

当前系统还没有彻底把这两层分开。

---

## 3. 目标方向

后续个人助理 agent 阶段，建议从“请求级回复流”升级为“会话级事件流”。

核心目标不是单纯把 SSE 连接拉长，而是：

> 把通信协议从“单轮回复”改成“多事件时间线”。

---

## 4. 目标模型

## 4.1 会话级长期 SSE

建议未来前端在进入某个聊天会话时，就建立一条长期 SSE 连接，而不是每次发送消息时再单独创建一次短连接。

建议形态：

1. 客户端建立 `conversation stream`
2. 后端持续向该连接推送事件
3. 客户端通过独立命令接口发送消息、确认、取消、重试等操作

即：

1. `SSE` 负责服务端持续推送
2. `HTTP / WebSocket` 负责客户端提交命令

---

## 4.2 单一时间线事件协议

后续不再把文本流和工具流拆成两套并行流再合并，而是统一成一条时间线事件流。

建议事件类型包括但不限于：

1. `session_meta`
2. `user_message_received`
3. `assistant_chunk`
4. `assistant_message_completed`
5. `tool_call_started`
6. `tool_call_finished`
7. `tool_call_failed`
8. `agent_status_changed`
9. `route_selected`
10. `human_confirmation_required`
11. `human_confirmation_resolved`
12. `artifact_generated`
13. `channel_delivery_status_changed`
14. `conversation_done`

前端只按事件到达顺序追加，不再自行重建工具和文本的先后关系。

---

## 4.3 一条用户输入，对应多条运行事件

后续应把“1 v 1”升级为“1 v 多”。

即：

1. 一条用户输入
2. 对应多条中间执行事件
3. 最终可能产出一条或多条 assistant 输出

例如：

1. 用户提问
2. 路由到报销分析技能
3. 数据集摘要检索开始
4. 数据集摘要检索完成
5. 结构获取开始
6. 结构获取完成
7. SQL 执行开始
8. SQL 执行完成
9. assistant 输出第一段解释
10. assistant 输出第二段总结

这比当前把全部过程硬塞进“一条 assistant 消息”更符合 agent 真实生命周期。

---

## 5. 建议分层

## 5.1 交互层

面向用户展示，仍然可以保留传统消息感知：

1. 用户消息
2. 助手消息
3. 渠道回执
4. 文件 / 卡片 / 引用

这一层负责：

1. 渲染最终对话体验
2. 折叠中间事件
3. 展示工具摘要或结构化卡片

---

## 5.2 运行层

面向 agent 生命周期，记录完整执行过程。

这一层负责：

1. 中间推理状态
2. 工具调用与结果
3. 路由与模式切换
4. 暂停 / 恢复 / 取消
5. 人工确认
6. 异步延续执行

后续数据库模型更适合演进为：

1. `chat_message`
   - 顶层消息
2. `chat_message_event`
   - 一条消息下的运行事件流
3. `chat_message_output` / `chat_artifact`
   - 最终结构化输出物

当前 `chat_context_message` 可视作未来运行层事件表的过渡形态。

---

## 7. 参考扣子式“提交与事件解耦”方案

## 7.1 调研结论

结合公开页面抓包与链路观察，可以把目标参考方案抽象为：

1. `chat` 接口不直接承担回复流
2. `chat` 接口只负责提交用户消息，并返回 `messageId`
3. 实时交互事件通过另一条长期连接推送
4. 前端根据 `messageId` 将后续事件归并到对应消息

对当前项目而言，可以先不直接上 `WebSocket`，但应优先吸收它的核心结构：

> 提交消息与消费事件解耦。

这比“一个请求同时负责提交 + 流式回复”的模型更适合后续 agent 化演进。

---

## 8. 推荐的中间态落地方案

## 8.1 保留 HTTP 提交消息

建议未来聊天主入口调整为：

1. 前端通过 `HTTP` 提交消息
2. 后端落库用户消息
3. 后端立即返回：
   - `sessionId`
   - `messageId`
   - `accepted`

示例：

```json
{
  "sessionId": "01KXXXXXXX",
  "messageId": 12345,
  "accepted": true
}
```

这个接口只负责：

1. 创建消息
2. 创建本次运行上下文
3. 启动异步执行

而不再直接承担回复文本的实时推送。

---

## 8.2 优先选择“会话级 SSE”，而不是“消息级 SSE”

当前调研后，一个很自然的方案是：

1. `chat` 返回 `messageId`
2. 前端再根据 `messageId` 建一条新的 SSE 连接

这个方案短期能工作，但不推荐作为正式演进方向。

原因：

1. 每发一条消息就要新建一条 SSE
2. 同一会话多消息并发时，连接数量会快速增长
3. 工具事件、状态事件、artifact 事件会分散到不同连接中
4. 后续改为 `WebSocket` 时迁移成本更高
5. 容易出现“消息已开始执行，但 SSE 尚未连上导致首包丢失”的竞态问题

因此更推荐：

1. 用户进入某个会话时，建立一条 `session-level SSE`
2. 所有后续消息事件都在这条连接中推送
3. 每个事件都携带 `messageId`
4. 前端按照 `messageId` 归并到对应消息节点

即：

1. `HTTP` 负责 `create message`
2. `SSE` 负责 `subscribe session events`

这套形态已经非常接近后续 `WebSocket` 化后的目标结构。

---

## 9. 推荐接口草案

## 9.1 消息提交接口

```http
POST /api/chat/messages
```

请求职责：

1. 提交用户消息
2. 返回 `messageId`

返回草案：

```json
{
  "sessionId": "01KXXXXXXX",
  "messageId": 12345,
  "accepted": true
}
```

---

## 9.2 会话事件订阅接口

```http
GET /api/chat/sessions/{sessionId}/events
```

请求职责：

1. 建立会话级 SSE
2. 接收该会话下所有实时事件

注意：

1. 连接的粒度应是 `session`
2. 不是单条 `message`

---

## 10. 推荐事件协议草案

每条事件都建议带统一公共字段：

```json
{
  "type": "message_delta",
  "sessionId": "01KXXXXXXX",
  "messageId": 12345,
  "timestamp": 1776736605653,
  "payload": {}
}
```

推荐事件类型：

1. `message_created`
2. `message_started`
3. `message_delta`
4. `message_completed`
5. `message_failed`
6. `tool_started`
7. `tool_finished`
8. `tool_failed`
9. `artifact_ready`
10. `run_status_changed`

示例：

```text
event: message_started
data: {"sessionId":"01KXXXXXXX","messageId":12345,"timestamp":1776736605653,"payload":{}}

event: message_delta
data: {"sessionId":"01KXXXXXXX","messageId":12345,"timestamp":1776736605654,"payload":{"delta":"您好，"}}

event: tool_started
data: {"sessionId":"01KXXXXXXX","messageId":12345,"timestamp":1776736605655,"payload":{"toolCallId":"tool_1","toolName":"search_dataset_summary"}}

event: tool_finished
data: {"sessionId":"01KXXXXXXX","messageId":12345,"timestamp":1776736605656,"payload":{"toolCallId":"tool_1","summary":"已完成数据集摘要检索"}}

event: message_completed
data: {"sessionId":"01KXXXXXXX","messageId":12345,"timestamp":1776736605657,"payload":{}}
```

---

## 11. 前端交互建议

前端建议流程：

1. 进入会话页面时，先建立 `session-level SSE`
2. 用户发送消息，调用 `POST /api/chat/messages`
3. 接收到返回的 `messageId`
4. 前端本地先创建一个待接收中的 assistant 占位
5. 在 SSE 中持续接收该 `messageId` 对应的事件
6. 按 `messageId` 拼接文本、状态、工具事件、artifact

前端不应再假设：

1. 一次 HTTP 请求必然对应一次完整回复
2. 一次回复只会有一条 assistant 文本

而应切换为：

1. 一条用户消息
2. 对应一个消息执行过程
3. 该过程下包含多条事件

---

## 12. 关键风险与规避

## 12.1 不推荐“先拿 messageId，再临时建立 message 级 SSE”

该方案存在明确竞态：

1. 后端可能在返回 `messageId` 后立即开始推送首个事件
2. 前端此时尚未完成 SSE 建连
3. 首个 `delta` / `started` 事件可能丢失

要规避这个问题，只能额外引入：

1. 事件缓存
2. 断点续传
3. 事件补拉

这会让中间态复杂度明显上升。

因此更优方案是：

1. 先建会话级 SSE
2. 再发消息

---

## 12.2 当前阶段不必直接升级到 WebSocket

虽然调研对象采用的是“提交消息 + 长连接事件推送”的结构，但当前项目不建议立即切到 `WebSocket`。

原因：

1. 当前真正需要优先升级的是“事件模型”，不是“传输协议”
2. 先做 `HTTP + session-level SSE`，即可完成提交与事件解耦
3. 后续若升级为 `WebSocket`，只需替换传输层，不必推翻消息模型

建议演进顺序：

1. 先升级为：
   - `HTTP create message`
   - `SSE subscribe session events`
2. 再在个人助理 agent 阶段升级为：
   - `HTTP / command API`
   - `WebSocket event bus`

---

## 13. 对现有表结构的兼容建议

为降低后续改造成本，建议保持以下兼容关系：

1. `chat_session`
   - 继续作为会话主表
2. `imessages`
   - 继续作为顶层消息表，先用其主键承载 `messageId`
3. `chat_context_message`
   - 继续作为运行过程事实流的过渡存储

中间态不要求立即新增运行表，但事件协议中建议预留未来字段：

1. `runId`
2. `eventId`
3. `parentMessageId`
4. `artifactId`

这样后续升级到 `session + message + run + event + artifact` 模型时，可以平滑迁移，而不是重做前后端交互。

---

## 6. 后续 Session + Message 改造方向

这一部分不要求现阶段实施，只用于后续个人助理 agent 阶段统一升级消息模型时作为输入。

## 6.1 当前模型现状

当前相关表大致承担的职责如下：

1. `chat_session`
   - 会话主表
   - 负责会话范围、展示快照、scope 绑定
2. `imessages`
   - 当前主消息表
   - 语义仍然偏“一问一答一条”
3. `chat_context_message`
   - 当前上下文事实表
   - 已开始承担运行层消息流 / 工具过程 / 压缩材料职责
4. `channel_session_binding`
   - 当前渠道会话与内部会话的绑定表
   - 负责渠道维度 session 映射与用户归属

这套结构在当前阶段能工作，但它的核心限制是：

> `imessages` 的形态更像“轮次结果表”，不是真正的事件流表。

---

## 6.2 后续建议的职责重分配

后续个人助理 agent 阶段，建议把“会话、消息、事件、输出物”拆清楚。

建议目标模型：

1. `chat_session`
   - 继续保留为会话主表
   - 承担会话级元数据、路由上下文、拥有者、状态、快照
2. `chat_message`
   - 顶层消息表
   - 一条用户输入、一条助手最终输出、渠道系统提示等，都作为顶层消息
3. `chat_message_event`
   - 运行事件流表
   - 一条顶层消息下可以挂多条事件
4. `chat_artifact` 或 `chat_message_output`
   - 结构化产物表
   - 存图表、卡片、附件、渲染结果、下载物等

对应关系建议为：

1. 一个 `chat_session`
   - 包含多条 `chat_message`
2. 一条 `chat_message`
   - 包含多条 `chat_message_event`
3. 一条 `chat_message`
   - 可以关联零条或多条 `chat_artifact`

---

## 6.3 `chat_session` 后续职责建议

`chat_session` 后续建议只做“会话范围”事实，不再承担太多消息过程信息。

建议保留或增强的职责：

1. 会话主键、会话编码
2. 所属用户 / owner
3. session type
4. scope 绑定
5. 当前 agent 模式 / persona / runtime profile
6. 最近活跃时间
7. 会话状态
   - `active`
   - `waiting_human`
   - `paused`
   - `closed`
8. 展示快照
   - last message
   - last event time
   - unread 标记

不建议继续往 `chat_session` 里塞过多轮次细节或工具过程细节。

---

## 6.4 `imessages` / `chat_message` 后续建议

当前 `imessages` 仍是核心消息表，但后续建议从“问答结果表”升级为“顶层消息表”。

建议后续一条 `chat_message` 的语义是：

1. 一个用户输入
2. 一个助手输出
3. 一个系统消息
4. 一个渠道输入映射消息

它不再直接承载完整工具过程，而只表达：

1. 这条消息是谁发的
2. 这条消息面向用户最终展示的主内容是什么
3. 这条消息是否有子事件流

建议后续至少具备以下字段语义：

1. `role`
   - `user`
   - `assistant`
   - `system`
   - `channel`
2. `message_kind`
   - `chat`
   - `command`
   - `status`
   - `artifact_notice`
3. `display_content`
   - 给用户看的主内容
4. `status`
   - `pending`
   - `streaming`
   - `completed`
   - `interrupted`
   - `error`
5. `parent_message_id`
   - 可选，用于“用户输入 -> 助手回复”关联

这样顶层消息仍然保留“聊天感”，但不会再强行把所有执行过程塞进一条记录。

---

## 6.5 `chat_context_message` / `chat_message_event` 后续建议

当前 `chat_context_message` 已经非常接近未来的运行事件表。

建议后续把它逐步演进为标准的 `chat_message_event`，专门承接以下事实：

1. assistant 中间文本片段
2. 工具调用开始
3. 工具调用完成
4. 路由决策
5. agent 状态变化
6. 人工确认请求
7. 人工确认结果
8. 中间产物生成
9. 渠道投递状态变化

也就是说，后续这张表更适合存：

1. `event_type`
2. `event_subtype`
3. `event_payload`
4. `event_order`
5. `related_tool_call_id`
6. `related_message_id`
7. `visibility`
   - `runtime_only`
   - `user_visible`
   - `debug_only`

这样后续“内容 + 工具 + 内容 + 工具 + 内容”的顺序展示，才有真正的数据基础。

---

## 6.6 `tool_trace` 在后续模型中的位置

当前已经把工具事实逐步收敛到 `tool_trace`，这个方向是对的。

后续建议：

1. 顶层消息表不直接记录工具全过程
2. 工具全过程统一进入事件表
3. 每次完整工具调用对应一条或一组标准事件

可以有两种落地方式：

1. 保守模式
   - 继续保留“一次工具调用一条 `tool_trace` 事件”
2. 精细模式
   - 拆成：
     - `tool_call_started`
     - `tool_call_finished`
     - `tool_call_failed`

如果未来要完整恢复 agent 执行时间线，精细模式会更好；如果偏重稳定和排障，保守模式也可接受。

---

## 6.7 渠道绑定表与新消息模型的关系

当前 `channel_session_binding` 不建议后续取消，反而更适合作为“外部会话到内部会话”的稳定桥接层。

后续建议关系如下：

1. 外部渠道 session
   - 继续通过 `channel_session_binding` 对应一个内部 `chat_session`
2. 外部渠道消息
   - 映射成内部 `chat_message`
3. 渠道投递状态 / 回执 / 重试
   - 进入 `chat_message_event`

这样可以把：

1. 外部消息标识
2. 内部消息标识
3. 投递结果
4. 恢复执行状态

统一串在一条标准链路上。

---

## 6.8 建议的演进顺序

后续做个人助理 agent 时，建议不要一次性重写所有表，而是按顺序演进：

1. 先把事件协议统一
2. 再让 `chat_context_message` 更明确地承担事件表职责
3. 再升级 `imessages` 为顶层消息表
4. 最后再补 `artifact / output` 结构化产物层

这样可以减少：

1. 线上消息链路震荡
2. 前端消息模型一次性大改
3. 渠道桥接层回归成本

---

## 6.9 当前结论

后续 `session + message` 改造不建议再围绕“一问一答表结构”继续做增强，而应明确演进为：

1. `chat_session`
   - 会话范围
2. `chat_message`
   - 顶层消息
3. `chat_message_event`
   - 执行事件流
4. `chat_artifact`
   - 结构化输出物

其中，当前 `chat_context_message` 是未来事件表最自然的过渡起点。

---

## 7. 与当前实现的关系

当前实现仍保留以下特征：

1. `meta -> message/tool/result/done` 的请求级 SSE 事件模型
2. 会话主结构仍偏“一问一答”
3. `tool_trace` 已开始承接工具调用事实
4. `chat_context_message` 已开始承担部分运行层事实源职责

因此当前阶段更适合的策略不是立即重构，而是：

1. 继续稳定现有链路
2. 减少消息写入重复
3. 收紧技能工具绑定范围
4. 为未来“会话级事件流”保留演进空间

---

## 8. 暂不实施范围

本草案当前只作为后续个人助理 agent 改造的输入，不纳入现阶段开发任务。

现阶段明确不做：

1. 不修改当前前后端 SSE 通信方式
2. 不引入长连接会话总线
3. 不重写前端聊天消息模型
4. 不把当前消息表彻底升级成事件表
5. 不改造渠道桥接为长连接订阅模式

原因：

1. 当前业务链路还在快速稳定阶段
2. 直接升级通信模型会联动后端、前端、渠道、消息模型多处重构
3. 更适合在“个人助理 agent”专项中统一设计和落地

---

## 9. 后续实施建议

建议未来在“个人助理 agent”专项中按以下顺序推进。

## 阶段 1：事件协议先行

先不改成长连接，只把当前请求级 SSE 输出改造成统一事件协议。

目标：

1. 明确事件类型
2. 保证时序稳定
3. 前端按时间线渲染

收益：

1. 先解决“内容 + 工具 + 内容”展示错位问题
2. 改动范围相对可控

---

## 阶段 2：会话级 SSE

在统一事件协议稳定后，再把连接从“请求级”升级为“会话级”。

目标：

1. 支持异步补发
2. 支持暂停与恢复
3. 支持渠道回执与状态统一推送

---

## 阶段 3：消息模型升级

最后再把底层数据结构升级为：

1. 顶层消息
2. 运行事件
3. 输出物 / 结构化结果

这样可以避免“通信协议”和“数据模型”同时大改带来的风险。

---

## 10. 当前结论

当前阶段，系统仍维持“请求级 SSE + 一问一答主结构”是合理的。

但从中长期看，个人助理 agent 最终应演进为：

> 会话级长期 SSE + 单一时间线事件协议 + 1 条用户输入对应多条运行事件

这个方向应在后续个人助理 agent 改造中统一落地，而不是零散穿插到当前业务稳定期。
