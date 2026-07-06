# Lingz Runtime 执行层第一、二、三阶段落地设计

**版本**: v1.2  
**日期**: 2026-04-27  
**状态**: 进入实现  
**适用范围**: Lingz Agent Runtime 执行层第一阶段落地

---

## 1. 目标

本设计稿对应当前已经统一好的 Lingz Agent Runtime 主链，目标是：

1. 不新起第二套 runtime
2. 不改 controller 与 SSE 主协议
3. 先把执行层正式收敛成 Runtime 子系统
4. 第一阶段先落：
   - execution model
   - workspace resolver
   - runtime tool context
   - native 文件类 action
5. 第二阶段继续落：
   - search
   - writeArtifact
   - artifact prefix 统一规则
6. 第三阶段继续落：
   - bash
   - workDir 逻辑路径
   - HOME/TMP 环境变量重写
   - 命令安全与输出截断

当前文档对应实现后，仍**不直接实现**：

1. docker backend
2. per-skill venv / requirements 安装

当前默认约束：

1. 所有 skill 当前阶段默认走 `native runtime`
2. skill 定义目录来自 `workspaces/public/skills/`
3. 不做 skill 到会话工作区的自动复制
4. Python 依赖环境设计见 `docs/sandbox/runtime-python-env-design.md`
5. 统一工具外观设计见 `docs/sandbox/runtime-tool-unification-design.md`

---

## 2. 范围与边界

### 2.1 上层不变

以下链路继续保持一套：

1. Controller
2. `LingzRuntimeRequest`
3. `ChatRuntimePreparedRequestAssembler`
4. `ChatRuntimeExecutor`
5. SSE 事件流
6. `conversation_session / message / event`

### 2.2 本阶段新增

本阶段只新增 Runtime 的执行子系统：

1. `execution/model`
2. `execution/workspace`
3. `execution/backend`
4. `execution/nativefs`
5. `execution/tool`

---

## 3. 第一、二阶段交付物

## 3.1 执行模型

新增统一模型：

1. `RuntimeExecutionMode`
2. `RuntimeExecutionAction`
3. `SandboxRoot`
4. `RuntimeWorkspace`
5. `RuntimeExecutionRequest`
6. `RuntimeExecutionResult`

当前已支持 action：

1. `FILE_READ`
2. `FILE_WRITE`
3. `LIST_DIR`
4. `STAT`
5. `SEARCH`
6. `WRITE_ARTIFACT`
7. `BASH`

## 3.2 工作区解析

新增：

1. `RuntimeExecutionProperties`
2. `RuntimeWorkspaceResolver`
3. `DefaultRuntimeWorkspaceResolver`

职责：

1. 根据 `userId + sessionId + runtimeSkillName + scopeType + scopeId` 解析当前工作区
2. 自动创建会话目录
3. 组装多根 `sandboxRoots`

默认 roots：

1. `session-workspace` -> `/workspace` -> 读写
2. `session-uploads` -> `/uploads` -> 只读
3. `session-outputs` -> `/outputs` -> 读写
4. `session-temp` -> `/temp` -> 读写
5. `session-logs` -> `/logs` -> 读写
6. `user-profile` -> `/profile` -> 默认只读
7. `skill-definition` -> `/skill` -> 只读

其中：

- 第一条 root 必须是主工作目录
- 模型看到的路径统一是逻辑路径，而不是宿主机路径

## 3.3 Runtime 工具上下文

新增：

1. `RuntimeToolContext`
2. `RuntimeToolInvocationContextHolder`

职责：

1. 在当前消息执行期间保存 runtime 作用域
2. 让系统工具在调用时获取：
   - `sessionId`
   - `userId`
   - `scopeType`
   - `scopeId`
   - `runtimeSkillName`
   - `runtimeMode`
   - `fileListJson`
   - `requestMessageId`
   - `assistantMessageId`

---

## 4. 执行层结构

第一阶段推荐目录：

```text
backend/src/main/java/lingzhou/agent/backend/business/chat/execution/
├── model/
├── workspace/
├── backend/
├── nativefs/
└── tool/
```

### 4.1 `RuntimeExecutionFacade`

职责：

1. 接收 runtime 系统工具调用
2. 解析工作区
3. 物化上传文件到 `/uploads`
4. 组装 `RuntimeExecutionRequest`
5. 调用 backend router

### 4.2 `RuntimeBackendRouter`

职责：

1. 按 runtime provider 分发 backend
2. provider 由独立 resolver 统一解析，不直接散落在业务里
3. 当前阶段默认 provider 为 `NATIVE`
4. 后续可扩展 `DOCKER`、`REMOTE`

### 4.2.1 `RuntimeProviderResolver`

职责：

1. 统一决定当前请求应走哪个 runtime provider
2. 优先消费会话或请求显式指定
3. 未显式指定时，回落到系统默认配置
4. 为后续 `native/docker/remote` 产品化预留稳定入口

### 4.3 `NativeRuntimeBackend`

职责：

1. 构建 `PathJail`
2. 将文件类 action 分发给 `NativeFileExecutor`

### 4.4 `NativeFileExecutor`

职责：

1. `FILE_READ`
2. `FILE_WRITE`
3. `LIST_DIR`
4. `STAT`

### 4.5 `LogicalPathResolver`

职责：

1. 将逻辑路径翻译为宿主机路径
2. 支持：
   - `/workspace`
   - `/uploads`
   - `/outputs`
   - `/temp`
   - `/logs`
   - `/skill`
   - `/profile`
3. 相对路径默认视为相对 `/workspace`

### 4.6 `PathJail`

职责：

1. 多根目录白名单校验
2. 读写权限控制
3. `toRealPath()` 防软链逃逸
4. 文件不存在时使用 `normalize()` 兜底

---

## 5. 工具注入策略

### 5.1 当前新增 runtime 系统工具

当前新增 `RuntimeSystemToolProvider`，并开始引入统一外观 `runtime_tool`。

底层仍支持以下 action：

1. `readFile(path)`
2. `writeFile(path, content)`
3. `listDir(path)`
4. `stat(path)`
5. `search(path, pattern, maxResults)`
6. `writeArtifact(folder, fileName, content, sourcePath, contentType)`
7. `bash(command, workDir, timeoutSeconds)`

这些工具的特点：

1. 统一走 `RuntimeExecutionFacade`
2. 统一受工作区与 `PathJail` 限制
3. 注册为 `systemRuntime=true`
4. 默认不作为手动 bindable 工具暴露
5. 后续将逐步收口为 `runtime_tool(action, params)` 单一入口

### 5.2 与现有工具的关系

当前已经替换：

1. `readFile`
2. `writeFile`
3. `writeArtifact`
4. `bash`

现有：

1. `runPython`

先保留历史实现，后续分别收敛到：

1. 直接复用 `bash("python3 ...")`

---

## 6. 上传文件与 `/uploads`

当前聊天上传文件仍存于 MinIO，并通过 `fileListJson` 持久化。

为了让 Runtime 能稳定使用 `/uploads/<filename>` 逻辑路径，第一阶段新增规则：

1. 每次进入 runtime 工具执行前，将当前消息关联上传文件物化到当前 session 的 `uploads/`
2. 物化来源仍以 `fileListJson` 为准
3. `/uploads` 默认只读

这意味着：

1. 模型后续读取附件优先使用 `/uploads/...`
2. 不再依赖 `chat-upload://...` 作为长期执行语义

---

## 7. 与现有 skill runtime 的接缝

### 7.1 filesystem skill

filesystem skill 继续通过：

- `SkillRuntimeRegistry`

注册，但其系统运行时工具来源改为：

- `GlobalToolRegistry.getSystemRuntimeToolCallbacks()`

### 7.2 skill chat

`SkillCatalogService.resolveSkillChatContext(...)` 在合并工具时，需要显式加入：

- `systemRuntimeToolCallbacks`

这样：

1. filesystem skill
2. 数据库存档 skill
3. 已发布 skill

都能共享同一套 runtime 系统工具。

---

## 8. 当前阶段验证标准

必须满足：

1. `readFile("/skill/SKILL.md")` 可读
2. `readFile("/uploads/<name>")` 可读
3. `writeFile("/workspace/out.md", "...")` 可写
4. `listDir("/workspace")` 可列目录
5. `stat("/workspace/out.md")` 可返回文件元数据
6. 写 `/skill/**` 被拒绝
7. 写 `/profile/**` 被拒绝
8. `search("/workspace", "keyword")` 可返回逻辑路径形式的匹配结果
9. `writeArtifact(content=...)` 可将文本写入 `/outputs` 后上传
10. `writeArtifact(sourcePath="/outputs/...")` 可直接交付会话产物
11. `bash("ls", "/workspace")` 可执行并返回输出
12. `bash("python3 /skill/scripts/xxx.py", "/workspace")` 可在当前工作区执行
13. `bash` 命令默认只能使用逻辑路径，不允许宿主机绝对路径
14. 后端可通过：

```bash
mvn -pl backend -q -DskipTests compile
```

---

## 9. 下一阶段顺序

在当前阶段稳定后，按以下顺序推进：

1. `DOCKER`

不建议在 docker 落地前再新加第二套命令执行协议。

---

## 10. 结论

第一阶段的核心不是“让模型会跑更多命令”，而是：

> 把 Lingz Agent Runtime 正式接上一层受工作区约束、受逻辑路径约束、可持续扩展到 native/docker 的执行子系统。

这一步完成后，Lingz Runtime 将首次具备清晰的：

1. 逻辑路径空间
2. 会话级工作区
3. 统一文件工具
4. 执行 backend 接缝

为后续 artifact、bash、docker 与 GUI runtime 打下统一底座。
