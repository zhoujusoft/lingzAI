# Runtime 执行协议设计

**版本**: v1.0  
**日期**: 2026-04-27  
**状态**: 方案设计  
**适用范围**: 当前项目 Lingz Agent Runtime 与 `native` / `docker` 沙盒执行层接缝定义

---

## 1. 目标

本设计解决的不是“工作区长什么样”，也不是“Docker 怎么挂载”，而是：

> **当前已经统一好的 Lingz Runtime，如何无分叉地接入执行层沙盒。**

本协议需要同时满足：

1. 上层聊天 / skill runtime 只有一套
2. 执行 backend 可切换 `native` / `docker`
3. 当前阶段所有 skill 默认进入 runtime，默认 backend 为 `native`
4. 文件、命令、产物、日志都能回流到当前会话事件体系
5. 尽量吸收 `lingclaw` 的成熟实现，而不是平地起新炉灶
6. 模型侧长期统一为单一 `runtime_tool` 入口，详见 `docs/sandbox/runtime-tool-unification-design.md`
7. provider 选择由服务端统一决策，模型侧不直接感知 `native` / `docker` / `remote`

---

## 2. 与现有 Lingz Runtime 的接缝

当前项目已经形成统一主链：

1. Controller
2. `LingzRuntimeRequest`
3. `ChatRuntimePreparedRequestAssembler`
4. `ChatRuntimeExecutor`

沙盒执行层不应再另起一套 runtime。

### 2.1 推荐接入位置

建议在 `ChatRuntimeExecutor` 下再引入统一执行门面：

- `RuntimeExecutionFacade`
- 或 `RuntimeBackendRouter`

职责：

1. 接收“需要 runtime 的工具动作”
2. 组装 `RuntimeExecutionRequest`
3. 先解析当前 runtime provider，再分发到对应 backend
4. 返回统一 `RuntimeExecutionResult`

### 2.2 明确禁止

不建议：

1. controller 直接判断 `native` / `docker`
2. skill 直接感知宿主机路径
3. 为 docker 再写一套独立 tool 协议
4. 为运行脚本单独暴露 `runPythonScript`、`runNodeScript` 这类业务专用工具

---

## 3. 参考 Lingclaw 的哪些能力

结合 `/Users/xiehb/workspace/lingclaw`，本项目应重点吸收以下实现思想。

### 3.1 `ToolConfig + sandboxRoot`

参考：

- `lingclaw/.../ToolConfig.java`

可吸收点：

1. `sandboxMode` 与 `sandboxRoot[]` 的组合表达
2. 多根挂载而不是单根目录
3. 每个 root 具备 `read/write` 权限
4. `docker` 下 root 需要容器内路径映射

不建议直接照搬的点：

1. 继续按 Agent 静态配置 `sandboxRoot`
2. 让业务层直接提供 docker `containerPath`

本项目应改为：

> **由 runtime 根据 `userId + sessionId + skill` 动态生成 roots。**

### 3.2 `PathJail`

参考：

- `lingclaw/.../PathJail.java`

可吸收点：

1. 多根路径监狱
2. `toRealPath()` 防止软链逃逸
3. 文件不存在时 `normalize()` 兜底
4. 读写权限按 mount 控制
5. `sandboxRoot[0]` 作为默认基准目录

本项目应直接复用其设计原则，用在 `native` backend。

### 3.3 `BashActionDelegate`

参考：

- `lingclaw/.../BashActionDelegate.java`

可吸收点：

1. 先校验，再执行
2. 宿主执行与 docker 执行共用一个 action 面
3. docker 下通过宿主路径到容器路径的翻译执行
4. `HOME / USERPROFILE / TMPDIR / TEMP / TMP` 重写

本项目应抽象成更通用的 runtime executor，而不是保留“编码工具 delegate”这一命名。

---

## 4. 核心原则

### 4.1 只有一套上层 runtime

不管执行层是：

1. 不启 runtime
2. `native`
3. `docker`

上层都只看到一套：

- 请求语义
- 会话语义
- SSE 事件语义

模型与 skill 模板看到的长期稳定协议始终是：

- `runtime_tool(action, params)`

而不是：

- `runtime_tool(..., provider="native")`
- `docker_tool(...)`
- `native_tool(...)`

### 4.2 backend 只负责执行，不定义工作区

工作区来源永远是：

- `docs/sandbox/workspace-design.md`

backend 只负责：

1. 接收 roots
2. 校验 roots
3. 建立执行上下文
4. 执行动作

### 4.3 Python / Node / CLI 属于 runtime 默认环境

不要再暴露：

- `runPythonScript`
- `runNodeScript`

推荐统一为：

- `coding_tool(action="bash")`
- 或上层封装为 runtime file/command action

脚本语言能力是 runtime 环境的一部分，不是业务工具本身。

### 4.4 artifact 是执行事实对象，不是静态 skill 文件

执行链路里：

1. 文件先写到 `outputs/`
2. 再登记为 artifact
3. 最终通过当前会话事件和 assistant 消息对外暴露

---

## 5. 最小执行协议

## 5.1 `RuntimeExecutionRequest`

建议定义最小协议：

```json
{
  "sessionId": "01KPX9K9S0T6JK6Q7Y191PVGMT",
  "userId": 1001,
  "runtimeSkillName": "docx-translation-pairs",
  "runtimeProvider": "NATIVE",
  "runtimeMode": "NATIVE",
  "defaultWorkDir": "/workspace",
  "sandboxRoots": [
    {
      "key": "session-workspace",
      "hostPath": "/workspaces/users/1001/sessions/01KPX.../workspace",
      "containerPath": "/workspace",
      "read": true,
      "write": true
    }
  ],
  "action": "bash",
  "payload": {
    "command": "python3 /skill/scripts/main.py",
    "workDir": "/workspace",
    "timeoutSeconds": 120
  }
}
```

字段说明：

### 必填字段

1. `sessionId`
2. `userId`
3. `runtimeMode`
4. `sandboxRoots`
5. `action`
6. `payload`

### 推荐字段

1. `runtimeSkillName`
2. `runtimeProvider`
2. `defaultWorkDir`
3. `requestMessageId`
4. `assistantMessageId`
5. `runId`

### 说明

1. `defaultWorkDir` 对模型和上层工具始终稳定，推荐逻辑值 `/workspace`
2. `runtimeProvider` 由服务端统一解析，当前默认值为 `NATIVE`
3. `runtimeMode` 当前主要用于兼容与底层执行模型复用，后续可逐步弱化为内部字段
4. `sandboxRoots` 是 runtime 动态装配结果
5. `payload` 随 action 变化

---

## 5.2 `SandboxRoot`

建议统一结构：

```json
{
  "key": "session-workspace",
  "hostPath": "/workspaces/users/1001/sessions/<sessionId>/workspace",
  "containerPath": "/workspace",
  "read": true,
  "write": true
}
```

字段规则：

1. `hostPath` 总是宿主机绝对路径
2. `containerPath` 只有 docker backend 真正使用，但建议始终生成
3. `read` / `write` 不能同时为 `false`
4. 第一条 root 必须是主工作目录

### 推荐固定映射

| key | 宿主机路径 | 容器内路径 | 默认权限 |
|-----|------------|------------|----------|
| `session-workspace` | `sessions/<sessionId>/workspace` | `/workspace` | 读写 |
| `session-uploads` | `sessions/<sessionId>/uploads` | `/uploads` | 只读 |
| `session-outputs` | `sessions/<sessionId>/outputs` | `/outputs` | 读写 |
| `session-temp` | `sessions/<sessionId>/temp` | `/temp` | 读写 |
| `session-logs` | `sessions/<sessionId>/logs` | `/logs` | 读写 |
| `user-profile` | `users/<userId>/profile` | `/profile` | 默认只读 |
| `skill-definition` | `public/skills/<skill>` | `/skill` | 只读 |

补充约束：

1. `skill-definition` 直接挂公共 skill 目录，不复制到会话目录
2. `native` 与后续 `docker` 都必须遵守同一套逻辑路径

---

## 5.3 `action` 最小集合

第一阶段建议只支持以下 action：

1. `bash`
2. `file_read`
3. `file_write`
4. `list_dir`
5. `search`
6. `stat`

说明：

1. 这些 action 已足够承载大部分 runtime skill
2. 上层如需 `readFile` / `writeFile` / `writeArtifact`，可以继续保留工具名，但内部最终收口到 runtime action
3. 先不要把 action 做得过宽

### `bash` payload

```json
{
  "command": "python3 /skill/scripts/main.py",
  "workDir": "/workspace",
  "timeoutSeconds": 120
}
```

### `file_read` payload

```json
{
  "path": "/skill/SKILL.md",
  "maxBytes": 65536
}
```

### `file_write` payload

```json
{
  "path": "/workspace/draft.md",
  "content": "..."
}
```

---

## 5.4 `RuntimeExecutionResult`

建议统一返回：

```json
{
  "success": true,
  "action": "bash",
  "exitCode": 0,
  "stdout": "...",
  "stderr": "",
  "timedOut": false,
  "workDir": "/workspace",
  "artifacts": [],
  "metadata": {
    "durationMs": 842
  }
}
```

字段规则：

1. 文件类 action 也返回同一壳子
2. `artifacts` 是本次 action 直接登记出的交付物
3. `metadata` 用来承载 backend 差异信息，但不污染主协议

---

## 6. Backend 抽象

## 6.1 顶层路由

建议定义：

### `RuntimeBackendRouter`

职责：

1. 根据 `runtimeMode` 选择 backend
2. 保证上层不感知 `native` / `docker`

接口示意：

```java
public interface RuntimeBackend {
    RuntimeExecutionResult execute(RuntimeExecutionRequest request);
}
```

```java
public class RuntimeBackendRouter {
    RuntimeExecutionResult execute(RuntimeExecutionRequest request);
}
```

---

## 6.2 `NativeRuntimeBackend`

职责：

1. 基于 `sandboxRoots` 构造 `PathJail`
2. 根据 action 做本地执行
3. 重写环境变量
4. 控制路径与命令安全

明确要求：

1. 绝不允许直接绕过 `PathJail`
2. 默认 `HOME/TMPDIR/TEMP/TMP/USERPROFILE` 指向当前 session 主目录
3. `workDir` 必须先过 `PathJail.resolveWorkDir`

---

## 6.3 `DockerRuntimeBackend`

职责：

1. 将 `sandboxRoots` 转成挂载
2. 确保容器已存在
3. 用 `docker exec` 执行 action
4. 返回统一结果

明确要求：

1. 容器内路径固定：
   - `/workspace`
   - `/uploads`
   - `/outputs`
   - `/temp`
   - `/logs`
   - `/profile`
   - `/skill`
2. 默认 `networkMode=none`
3. 同一套 request，不允许业务层知道 docker 细节

---

## 7. RuntimeWorkspaceResolver

这是本项目最重要的实现组件之一。

职责：

1. 根据 `userId + sessionId + skill` 计算目录
2. 确保目录存在
3. 生成本次 `sandboxRoots`
4. 决定是否挂 `profile`
5. 决定是否挂额外只读模板目录

### 7.1 输入

```json
{
  "userId": 1001,
  "sessionId": "01KPX...",
  "runtimeSkillName": "docx-translation-pairs",
  "runtimeMode": "NATIVE",
  "allowProfileWrite": false,
  "allowTemplateRead": false
}
```

### 7.2 输出

1. `workspace/`
2. `uploads/`
3. `outputs/`
4. `temp/`
5. `logs/`
6. `meta/`
7. `profile/`
8. `skill-definition/`
9. `sandboxRoots[]`

### 7.3 默认挂载策略

必挂：

1. `session-workspace`
2. `session-uploads`
3. `session-outputs`
4. `session-temp`
5. `session-logs`
6. `skill-definition`

可选挂：

1. `user-profile`
2. 极小范围模板目录

默认不挂：

1. 整个 `public/skillstudio`
2. 项目根目录
3. 其他用户目录
4. 整个 `runtime-envs`

---

## 8. `coding_tool` 与上层工具的关系

## 8.1 推荐关系

建议区分两层：

### Runtime 默认动作层

- `bash`
- `file_read`
- `file_write`
- `list_dir`
- `search`

### 业务友好工具层

- `readFile`
- `writeFile`
- `writeArtifact`
- 未来的 `execScript`

原则：

1. 上层可以保留现有业务工具名
2. 但内部最终收口到 runtime action
3. 不再出现“某个 skill 自己直连宿主机文件系统”

### 8.2 与 Lingclaw 的对应

`lingclaw` 中：

- `coding_tool(action="bash")`
- `coding_tool(action="file_read")`

本项目可以吸收这个思想，但不必强制前端或业务层永远只看到 `coding_tool` 这一个名字。

关键不是工具名字，而是：

> **最终只有一套 runtime action 面。**

---

## 9. Artifact 接入协议

建议定义独立登记动作：

### `RuntimeArtifactRegistration`

输入：

```json
{
  "sessionId": "01KPX...",
  "userId": 1001,
  "runtimeMode": "NATIVE",
  "sourcePath": "/outputs/report.docx",
  "artifactType": "document",
  "displayName": "翻译结果.docx",
  "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
}
```

输出：

```json
{
  "artifactId": "art_xxx",
  "path": "artifact://documents/...",
  "downloadUrl": "/api/files/artifacts/.../download?fileName=..."
}
```

原则：

1. 先有 `outputs/` 文件
2. 再登记 artifact
3. artifact 是 runtime 事实对象
4. assistant 最终消息只引用 artifact，不直接暴露宿主机路径

---

## 10. 与当前会话 / SSE 模型的关系

当前项目不需要为沙盒重做会话模型。

建议只补 runtime 元数据和事件。

### 10.1 推荐事件类型

1. `RUNTIME_ALLOCATED`
2. `RUNTIME_ACTION_STARTED`
3. `RUNTIME_ACTION_COMPLETED`
4. `RUNTIME_ACTION_FAILED`
5. `ARTIFACT_REGISTERED`

### 10.2 推荐元数据

放在 message params 或 context payload 中：

1. `runtimeMode`
2. `runtimeSkillName`
3. `workspaceSummary`
4. `sandboxRootsSummary`
5. `artifacts`

### 10.3 与当前前端 SSE 的关系

不新增第二套事件协议。

仍然复用现有：

1. `tool`
2. `result`
3. `message`
4. `meta`
5. `done`
6. `error`

runtime backend 只负责提供执行结果，SSE 仍由当前 Lingz Runtime 统一包装。

---

## 11. 推荐落地顺序

### 第一阶段

1. 新增 `RuntimeExecutionRequest / Result / SandboxRoot`
2. 新增 `RuntimeWorkspaceResolver`
3. 新增 `RuntimeBackendRouter`
4. 实现 `NativeRuntimeBackend`
5. 先接 `bash + file_read + file_write`

### 第二阶段

1. 接 artifact 登记
2. 接 `writeArtifact`
3. 把更多文件工具收口到 runtime

### 第三阶段

1. 实现 `DockerRuntimeBackend`
2. 接 session 级容器复用
3. 加资源和网络限制

---

## 12. 最终结论

结合当前项目已有文档与 `lingclaw` 参考实现，建议明确以下结论：

1. **工作区目录语义继续由本项目定义**
2. **沙盒执行思想吸收 `lingclaw` 的 `sandboxRoot + PathJail + DockerSandbox + coding_tool action`**
3. **上层 Lingz Runtime 继续只有一套**
4. **执行层通过统一 `RuntimeExecutionRequest` 接入**
5. **`native` 和 `docker` 只是 backend 切换，不改变业务协议**
6. **artifact 作为 runtime 事实对象单独登记，不直接暴露宿主机路径**

一句话总结：

> **Lingz Runtime 负责“会话、提示、工具、SSE”；Sandbox Runtime 负责“工作区、路径、命令、文件、产物执行”。两者通过一套最小执行协议对接。**
