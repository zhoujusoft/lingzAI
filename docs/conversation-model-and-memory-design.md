# 会话模型与会话记忆设计草案

**版本**: v0.1  
**日期**: 2026-04-21  
**状态**: 设计落地，暂不改代码  
**适用范围**: 聊天 / 技能 / 数据集 / 渠道 / 后续个人助理 agent

---

## 1. 目标

本设计用于统一以下问题：

1. 会话、主消息、运行事件三层模型如何拆分
2. 一条用户消息对应多条 assistant 消息如何表达
3. `内容 + 工具 + 内容` 这类真实执行过程如何存储与展示
4. 会话记忆应该从哪些表、哪些记录中抽取，而不是继续混在旧结构里

本设计暂不要求立刻实施，但后续代码改造应尽量按此模型收敛。

---

## 2. 三张主表的最终语义

后续聊天主模型统一为三张主表：

1. `conversation_session`
2. `conversation_message`
3. `conversation_event`

它们的职责边界如下：

### 2.1 `conversation_session`

定义：

> 一个会话容器，一行代表一个独立会话线程。

只负责：

1. 会话身份
2. 会话归属
3. 会话范围
4. 列表展示快照

不负责：

1. 单条消息内容
2. 工具过程
3. 模型上下文正文
4. 运行时事件

---

### 2.2 `conversation_message`

定义：

> 会话时间线中的主展示节点，一行就是前端默认看到的一条聊天气泡 / 卡片节点。

只负责：

1. 用户消息
2. assistant 主回复
3. assistant 后续补充回复
4. 系统通知型主消息

不负责：

1. 工具开始 / 工具结束
2. 路由状态
3. 每个 delta chunk
4. 工具结果明细

关键结论：

> `conversation_message` 不是“消息组”，而是“主展示节点”。

前端主时间线默认按 `conversation_message` 渲染。

---

### 2.3 `conversation_event`

定义：

> 某条主消息在执行过程中产生的事实事件流。

负责：

1. 用户消息创建事件
2. assistant 开始处理事件
3. 工具开始 / 完成 / 失败事件
4. 路由选择事件
5. 产物生成事件
6. 最终完成 / 失败 / 取消事件
7. 历史摘要事件

关键结论：

> `conversation_event` 是运行层真相，不是默认聊天时间线。

前端仅在需要查看执行过程时，才按 `conversation_event` 展开渲染。

---

## 3. 会话类型

`conversation_session.session_type` 建议统一枚举为：

1. `GENERAL_CHAT`
2. `SKILL_CHAT`
3. `PUBLISHED_SKILL_CHAT`
4. `DATASET_CHAT`
5. `KNOWLEDGE_QA`
6. `CHANNEL_CHAT`
7. 后续预留：`AGENT_CHAT`

说明：

1. 当前系统中的“发布技能对话”应在最终模型中作为独立类型存在
2. 不再继续沿用语义偏实现细节的命名，例如 `SKILL_CHAT_APP`

---

## 4. 三张表的字段草案

## 4.1 `conversation_session`

建议字段：

1. `id`
2. `session_code`
3. `session_type`
4. `scope_type`
5. `scope_id`
6. `title`
7. `status`
8. `last_message_id`
9. `last_message_preview`
10. `create_user_id`
11. `created_at`
12. `updated_at`
13. `archived_at`

说明：

1. `scope_type` 用于补足 `scope_id` 的语义，避免仅靠一个 ID 猜类型
2. `title` 是前端展示标题
3. `last_message_preview` 仅是列表快照，不是事实源

---

## 4.2 `conversation_message`

建议字段：

1. `id`
2. `session_id`
3. `message_code`
4. `parent_message_id`
5. `role`
6. `message_kind`
7. `content`
8. `content_format`
9. `status`
10. `error_code`
11. `error_message`
12. `params_json`
13. `attachments_json`
14. `artifact_summary_json`
15. `sequence_no`
16. `create_user_id`
17. `created_at`
18. `updated_at`
19. `completed_at`

字段约束建议：

1. `role`:
   - `USER`
   - `ASSISTANT`
   - `SYSTEM`
2. `message_kind`:
   - `INPUT`
   - `REPLY`
   - `FOLLOWUP`
   - `NOTICE`
   - `SUMMARY`
   - `ARTIFACT_CARD`
3. `status`:
   - `PENDING`
   - `STREAMING`
   - `COMPLETED`
   - `FAILED`
   - `CANCELLED`

关键关系：

1. `parent_message_id` 用于表达：
   - 一条 user message
   - 对应一条或多条 assistant message

例如：

1. `USER / INPUT / id=1001`
2. `ASSISTANT / REPLY / id=1002 / parent_message_id=1001`
3. `ASSISTANT / FOLLOWUP / id=1003 / parent_message_id=1001`

---

## 4.3 `conversation_event`

建议字段：

1. `id`
2. `session_id`
3. `message_id`
4. `event_code`
5. `parent_event_id`
6. `event_type`
7. `event_subtype`
8. `sequence_no`
9. `summary_text`
10. `payload_json`
11. `create_user_id`
12. `created_at`

第一版建议支持的 `event_type`：

1. `USER_MESSAGE_CREATED`
2. `ASSISTANT_MESSAGE_STARTED`
3. `ROUTE_SELECTED`
4. `TOOL_STARTED`
5. `TOOL_FINISHED`
6. `TOOL_FAILED`
7. `ARTIFACT_READY`
8. `MESSAGE_COMPLETED`
9. `MESSAGE_FAILED`
10. `MESSAGE_CANCELLED`
11. `SUMMARY_SNAPSHOT`

说明：

1. `summary_text` 用于人类可读摘要、前端折叠展示、上下文摘要拼装
2. `payload_json` 保存结构化真相
3. 第一版不强制保存每个 delta chunk

---

## 5. 默认前端渲染规则

默认聊天时间线：

1. 按 `conversation_message` 渲染
2. 不直接平铺 `conversation_event`

展开执行过程时：

1. 读取当前 `message_id` 下的 `conversation_event`
2. 以折叠面板、详情抽屉或调试视图形式展示

原因：

1. 如果把 event 直接平铺进主时间线，会导致 `内容 + 工具 + 内容` 过碎
2. 主时间线应优先面向用户可读性
3. event 层面向运行过程和审计

---

## 6. 会话记忆的来源设计

这是本设计的核心部分之一。

后续“会话记忆”不应继续从旧式“一问一答表”整体拼，而应明确从以下两层抽取：

1. `conversation_message`
2. `conversation_event`

其中：

1. `conversation_message` 提供“主对话语义”
2. `conversation_event` 提供“运行过程事实”

---

## 7. 会话记忆到底取哪些记录

## 7.1 必须纳入会话记忆的来源

### 来源 A：`conversation_message`

建议纳入：

1. `role = USER`
2. `role = ASSISTANT`
3. `role = SYSTEM` 且属于真正需要被模型感知的通知型主消息

用途：

1. 还原用户问题
2. 还原 assistant 已给出的对外结论
3. 维持对话连续性

结论：

> 会话记忆的主骨架来自 `conversation_message`。

---

### 来源 B：`conversation_event`

建议只纳入“对后续回答有事实价值”的事件，不是所有事件都进记忆。

建议纳入：

1. `TOOL_FINISHED`
2. `TOOL_FAILED`
3. `ROUTE_SELECTED`
4. `ARTIFACT_READY`
5. `SUMMARY_SNAPSHOT`
6. 必要时纳入：
   - `MESSAGE_FAILED`
   - `MESSAGE_CANCELLED`

用途：

1. 让模型知道之前已经查过什么
2. 让模型知道工具给出过哪些关键结果
3. 避免重复调用相同工具
4. 避免丢失已生成 artifact 的事实

结论：

> 会话记忆的“过程事实补充层”来自 `conversation_event`。

---

## 7.2 `conversation_event` 的记忆参与策略必须配置化

这里不建议把“哪些事件能进记忆、哪些事件必须有 `summary_text`”写死在业务代码分支里。

原因：

1. 事件类型会持续增加
2. 不同事件未来可能调整记忆策略
3. 如果每次都改 if/else，维护成本会越来越高
4. 后续如果要升级成数据库配置，也需要先有统一策略模型

因此建议引入：

1. `conversation_event_type_config`
   - 当前阶段先放在代码注册表
   - 后续需要时再升级到数据库表

建议至少包含以下配置项：

1. `memory_enabled`
   - 是否允许该事件参与会话记忆
2. `summary_mode`
   - `NONE`
   - `OPTIONAL`
   - `REQUIRED`
   - `DERIVED_FROM_PAYLOAD`
3. `memory_source`
   - `SUMMARY_TEXT`
   - `PAYLOAD_JSON`
   - `HYBRID`
4. `summary_participation`
   - 是否允许被历史压缩摘要吸收
5. `default_priority`
   - 多类事件同时入窗时的优先级

当前阶段的推荐默认值：

| event_type | memory_enabled | summary_mode | memory_source | summary_participation | 说明 |
|------|------|------|------|------|------|
| `USER_MESSAGE_CREATED` | false | `NONE` | `SUMMARY_TEXT` | false | 用户主消息已在 `conversation_message` |
| `ASSISTANT_MESSAGE_STARTED` | false | `NONE` | `SUMMARY_TEXT` | false | 纯运行状态 |
| `ROUTE_SELECTED` | true | `OPTIONAL` | `SUMMARY_TEXT` | true | 路由原因可能影响后续回答 |
| `TOOL_STARTED` | false | `NONE` | `SUMMARY_TEXT` | false | 默认不进记忆 |
| `TOOL_FINISHED` | true | `REQUIRED` | `SUMMARY_TEXT` | true | 工具结果是关键事实 |
| `TOOL_FAILED` | true | `REQUIRED` | `SUMMARY_TEXT` | true | 失败事实也可能影响后续策略 |
| `ARTIFACT_READY` | true | `REQUIRED` | `SUMMARY_TEXT` | true | 需要保留“已生成产物”的事实 |
| `MESSAGE_COMPLETED` | false | `NONE` | `SUMMARY_TEXT` | false | assistant 主结论已在 `conversation_message` |
| `MESSAGE_FAILED` | false | `OPTIONAL` | `SUMMARY_TEXT` | false | 默认不进记忆，可按需开启 |
| `MESSAGE_CANCELLED` | false | `OPTIONAL` | `SUMMARY_TEXT` | false | 默认不进记忆，可按需开启 |
| `SUMMARY_SNAPSHOT` | true | `REQUIRED` | `SUMMARY_TEXT` | false | 自身就是历史摘要锚点 |

关键约束：

1. 只有 `memory_enabled = true` 的事件，才允许进入记忆拼装
2. 如果 `summary_mode = REQUIRED`，则没有 `summary_text` 时，该事件不能进入记忆
3. 如果 `summary_mode = DERIVED_FROM_PAYLOAD`，则允许从 `payload_json` 派生可入记忆的摘要文本
4. 事件是否写库，与事件是否参与记忆，是两件独立的事

结论：

> `conversation_event` 负责存事实；是否参与记忆，由事件类型策略配置决定。

---

## 7.3 不建议默认纳入会话记忆的 event

以下事件默认不建议进入模型上下文：

1. `USER_MESSAGE_CREATED`
2. `ASSISTANT_MESSAGE_STARTED`
3. `TOOL_STARTED`
4. 每个 `delta chunk`
5. 纯状态跳转但没有事实增量的 `STATUS_CHANGED`

原因：

1. 对模型继续回答帮助不大
2. 会污染上下文窗口
3. 容易引入重复和噪音

---

## 8. 会话记忆的拼装建议

后续记忆拼装建议分成三层：

### 第 1 层：主消息骨架

来自 `conversation_message`：

1. 最近若干条 `USER`
2. 最近若干条 `ASSISTANT`

这是最核心的对话骨架。

---

### 第 2 层：关键事件事实

来自 `conversation_event`：

1. 只读取 `memory_enabled = true` 的事件
2. 且满足其对应的 `summary_mode`
3. 优先读取事件的 `summary_text`
4. 仅在策略允许时，才从 `payload_json` 派生补充

这一层不直接拼 raw JSON，而优先拼 `summary_text`。

---

### 第 3 层：历史摘要

来自 `SUMMARY_SNAPSHOT` 事件：

1. 当会话累计过长时，把更早历史压缩成结构化摘要
2. 摘要本身作为一种事件保留
3. 后续拼装上下文时：
   - 先取最近一条 summary
   - 再接 summary 之后的 message 和关键 event

结论：

> 后续历史压缩建议从“上下文消息表”思路，正式升级为“摘要事件”思路。

---

## 9. 会话记忆的推荐读取策略

推荐逻辑：

1. 先找当前 session 最近一条 `SUMMARY_SNAPSHOT`
2. 如果存在：
   - 把该摘要作为记忆起点
   - 再读取摘要之后的 `conversation_message`
   - 再读取摘要之后的关键 `conversation_event`
3. 如果不存在：
   - 直接读取最近窗口内的 `conversation_message`
   - 以及同窗口内的关键 `conversation_event`

窗口控制建议：

1. `message` 层控制主对话条数
2. `event` 层只取关键事件，不取全量事件
3. 记忆预算优先保留：
   - 最近 user/assistant 主消息
   - 最近工具完成结果
   - 最近 summary

---

## 10. 关于“内容 + 工具 + 内容”的记忆处理

模型真实过程可能是：

1. assistant 先输出一段
2. 调工具
3. assistant 再输出一段
4. 再调工具
5. 最终完成

但在记忆层不建议直接把这些碎片照单全收。

建议处理方式：

1. assistant 主展示结果，最终收敛进 `conversation_message.content`
2. 工具关键结果进入 `conversation_event.summary_text`
3. 记忆拼装时：
   - assistant 最终结果来自 `message`
   - 工具关键信息来自 `event`

结论：

> 会话记忆不必强求完全还原“交错流式碎片”，而应优先保留“最终可继续推理的事实”。

---

## 11. 关于“一条 user 对多条 assistant”的记忆处理

后续模型中，一条 user message 对多条 assistant message 是合法结构。

例如：

1. 一条主回复
2. 一条后续补充
3. 一条异步通知

记忆拼装时建议：

1. 都作为独立 `conversation_message` 节点参与上下文
2. 保留 `parent_message_id` 关系用于前端归组
3. 模型上下文层不需要把它们硬压成“一问一答”结构

结论：

> 会话记忆层应接受“1 user -> n assistant”是正常结构，而不是异常情况。

---

## 12. 最终结论

三张表的职责最终定义为：

1. `conversation_session`
   - 会话容器
2. `conversation_message`
   - 主时间线节点
3. `conversation_event`
   - 运行事实事件流

会话记忆的来源最终定义为：

1. 主骨架：
   - `conversation_message`
2. 关键过程事实：
   - `conversation_event` 中的关键完成型事件
3. 长历史压缩：
   - `SUMMARY_SNAPSHOT`

不建议继续采用旧式思路：

1. 只从“一问一答消息表”拼记忆
2. 把所有工具过程直接塞到主消息表
3. 把所有 event 平铺进前端主时间线

后续改造应尽量朝这个方向一次性收敛。
