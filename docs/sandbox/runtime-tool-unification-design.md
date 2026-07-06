# Runtime Tool 统一设计

**版本**: v2.0  
**日期**: 2026-04-28  
**状态**: 方案落地  
**适用范围**: Lingz Agent Runtime 工具层统一为 `runtime_tool`

---

## 1. 目标

当前 Lingz Runtime 的执行内核已经统一，但模型可见工具仍需要进一步收口。

本设计的目标是：

> **模型长期只看到一个运行时工具入口 `runtime_tool`，且其 action 白名单必须足够小、足够稳、足够可控。**

本轮收口要解决的问题：

1. 去掉开放式 shell 能力
2. 减少模型在工作区的探索自由度
3. 保留最小可用的文件工作流能力
4. 为后续 `run_python` 预留稳定扩展点
5. 将 Python 执行统一收敛到平台受控环境

---

## 2. 核心原则

### 2.1 工具对模型要“精”，不要“多”

模型长期只应理解一套统一运行时工具：

1. `runtime_tool`

而不是记忆一串散装工具名：

1. `readFile`
2. `writeFile`
3. `listDir`
4. `bash`
5. `writeArtifact`

### 2.2 runtime_tool 是受控工作区工具，不是通用 shell

当前阶段的 `runtime_tool` 定位是：

> **受控文件工作区工具**

不是：

> **开放式命令执行器**

因此：

1. 不再长期保留 `bash`
2. 不鼓励模型进行大范围探索式搜索
3. 后续脚本执行能力通过受控 action 扩展，而不是重新开放 shell
4. `run_python` 一旦开放，也只能运行平台受控的 Python 环境，而不是任意解释器

### 2.3 后端执行内核不推翻

当前已落地的：

1. `RuntimeExecutionFacade`
2. `RuntimeExecutionAction`
3. `NativeRuntimeBackend`
4. `RuntimeArtifactService`
5. `NativeFileExecutor`

继续保留。

`runtime_tool` 只是模型协议统一和能力收口，不改变执行架构方向。

---

## 3. 为什么必须去掉 bash

`bash + file_write` 组合起来，相当于给模型暴露了：

1. 可写磁盘
2. 可执行命令
3. 可自行拼装脚本和运行逻辑

这会把 runtime 边界从：

1. 输入文件
2. 中间文件
3. 输出产物

扩展成：

1. 半开放宿主执行环境

带来的问题：

1. 技能执行稳定性下降
2. 安全边界复杂度明显上升
3. native / docker / remote 三种 backend 更难统一
4. 模型更容易绕过文件协议，直接拼命令解决问题

因此本轮设计明确结论：

> **去掉 `bash`，后续如需脚本执行，改为受控 `run_python`。**

---

## 4. 当前阶段 action 白名单

当前阶段建议保留以下 action：

1. `file_read`
2. `file_write`
3. `list_dir`
4. `stat`
5. `write_artifact`
6. `run_python`

当前阶段明确不保留：

1. `search`
2. `bash`

---

## 5. 对模型暴露的统一协议

统一工具入口：

```json
{
  "tool": "runtime_tool",
  "action": "file_read | file_write | list_dir | stat | write_artifact | run_python",
  "params": {}
}
```

注意：

1. provider 不进入模型可见协议
2. backend 是 `native`、`docker`、`remote`，模型都不感知
3. 上层 skill 模板、prompt、SSE 事件语义都保持统一

---

## 6. action 定义

### 6.1 `file_read`

用于读取工作区、uploads、skill 定义目录中的文件。

```json
{
  "action": "file_read",
  "params": {
    "path": "/uploads/input.docx"
  }
}
```

### 6.2 `file_write`

用于写入中间文件、草稿文件或结构化结果。

```json
{
  "action": "file_write",
  "params": {
    "path": "/workspace/draft.md",
    "content": "..."
  }
}
```

### 6.3 `list_dir`

用于查看受控目录结构。

```json
{
  "action": "list_dir",
  "params": {
    "path": "/workspace"
  }
}
```

### 6.4 `stat`

用于查看文件或目录属性。

```json
{
  "action": "stat",
  "params": {
    "path": "/outputs/result.docx"
  }
}
```

### 6.5 `write_artifact`

用于生成最终交付产物并上传。

```json
{
  "action": "write_artifact",
  "params": {
    "folder": "translation",
    "fileName": "translated-output.docx",
    "content": "",
    "sourcePath": "/outputs/translated-output.docx",
    "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  }
}
```

### 6.6 `run_python`

用于执行 skill 自带的受控 Python 脚本。

```json
{
  "action": "run_python",
  "params": {
    "scriptPath": "/skill/scripts/main.py",
    "args": ["--input", "/workspace/input.json", "--output", "/outputs/result.docx"],
    "workDir": "/workspace",
    "timeoutSeconds": 120
  }
}
```

补充约束：

1. `scriptPath` 只能指向 `/skill/scripts/**` 或 `/workspace/**`
2. 运行时统一基于平台受控 Python 环境
3. 当前平台 Python 版本规范见 `runtime-python-env-design.md`，统一按 `Python 3.11` 口径
4. 不允许模型指定任意解释器路径
5. 不允许把 `run_python` 退化成 shell 替代品

---

## 7. 当前阶段不开放的 action

### 7.1 `search`

不保留原因：

1. 会鼓励模型在工作区里做开放式探索
2. 会削弱 skill 的流程型执行边界
3. 很多场景其实可以通过更明确的输入路径和目录协议解决

### 7.2 `bash`

不保留原因：

1. 开放性过大
2. 容易绕开受控协议
3. 会把 runtime 变成通用 shell，而不是平台执行层

---

## 8. 为什么保留这六个 action

当前保留的六个 action 已经能覆盖大多数文件工作流 skill：

1. `file_read`
   - 读输入
   - 读 skill 参考文件

2. `file_write`
   - 写中间稿
   - 写解析结果

3. `list_dir`
   - 看目录
   - 枚举输入文件

4. `stat`
   - 看文件元信息
   - 判断文件是否存在

5. `write_artifact`
   - 写最终交付物

6. `run_python`
   - 执行受控 Python 脚本
   - 生成高保真文档或结构化产物

这套能力已经足够支撑：

1. 文档翻译
2. 文档重组
3. 中间稿生成
4. 多文件整理
5. skill 工坊写文件类场景

---

## 9. 与 Python 执行能力的关系

本轮收口并不意味着 Lingz Runtime 永远不支持脚本执行。

相反，推荐路线是：

1. 去掉 `bash`
2. 保留文件工作流能力
3. 通过 `run_python` 增加受控执行能力

也就是说：

1. `runtime_tool` 仍然是唯一入口
2. 只是 action 从开放 shell 改成受控 Python 执行

详见：

[runtime-python-env-design.md](/Users/xiehb/workspace/lingzhou-agent/docs/sandbox/runtime-python-env-design.md)

---

## 10. 返回结构

`runtime_tool` 的返回继续复用现有：

1. `RuntimeExecutionResult`
2. `RuntimeToolResultFormatter`

也就是说：

1. 工具外观统一
2. 结果协议继续复用
3. SSE 工具事件、artifact 抽取、前端展示逻辑不推翻

---

## 11. 对 Skill 模板的要求

后续所有 skill 模板和工坊生成内容，都应按以下口径描述：

1. 读取输入使用 `runtime_tool(action="file_read")`
2. 写中间文件使用 `runtime_tool(action="file_write")`
3. 查看目录使用 `runtime_tool(action="list_dir")`
4. 查看文件属性使用 `runtime_tool(action="stat")`
5. 写最终产物使用 `runtime_tool(action="write_artifact")`
6. 需要脚本执行时使用 `runtime_tool(action="run_python")`

明确不再继续写：

1. `bash`
2. `search`
3. 分散的旧工具名 `readFile`、`writeFile`、`writeArtifact`

---

## 12. 实施顺序

### Phase 1

完成以下动作：

1. `RuntimeSystemToolProvider` 的工具说明改为六个 action 白名单
2. `RuntimeToolActionMapper` 去掉 `search` 和 `bash`
3. 相关模板与 prompt 同步收口

### Phase 2

补强：

1. Python 环境缓存与依赖预热
2. skill 级 `.venv` 复用逻辑
3. `run_python` 模板、观测与管理能力

---

## 13. 最终结论

当前阶段 Lingz Runtime 的统一工具应收敛为：

1. 单一工具入口：`runtime_tool`
2. 最小 action 白名单：
   - `file_read`
   - `file_write`
   - `list_dir`
   - `stat`
   - `write_artifact`
   - `run_python`
3. 明确不开放：
   - `search`
   - `bash`
4. 其中 Python 执行统一通过 `run_python` 收口，并按平台受控 Python 环境执行

这条路线既保留了文件工作流能力，也为后续离线 Python skill、native/docker/remote 多 backend 统一留出了稳定扩展面。
