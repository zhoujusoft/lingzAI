# 基于 AgentScope 的 Docker 沙盒与 GUI 云电脑路线

**版本**: v1.0  
**日期**: 2026-05-08  
**状态**: 技术路线沉淀  
**适用范围**: 后续 Docker 沙盒、Docker GUI 云电脑、浏览器自动化与业务 MCP 工具扩展

---

## 1. 核心结论

后续 Docker 沙盒与 Docker GUI 云电脑能力建议采用：

> **站在 AgentScope Runtime 镜像与 MCP 体系之上做增强，而不是从零构建整套图形沙盒。**

本项目负责：

1. 用户、权限、会话、任务编排
2. workspace 目录分配和挂载策略
3. Docker 容器生命周期管理
4. VNC 地址代理与前端展示
5. MCP 工具调用代理
6. Agent 规划到工具调用的调度
7. 业务 MCP 工具扩展和审计

AgentScope Runtime 负责提供成熟基座：

1. Docker 镜像构建结构
2. FastAPI runtime 服务
3. MCP `list_tools` / `call_tool` 协议入口
4. Xvfb / xfce4 / x11vnc / noVNC / websockify / nginx 图形链路
5. Playwright MCP 浏览器自动化能力
6. 可扩展的自定义 sandbox 镜像机制

---

## 2. 为什么选择 AgentScope 作为基座

### 2.1 不从零构建的原因

Docker GUI 云电脑不是单一 Dockerfile 问题，而是多个进程稳定协作：

1. 浏览器运行环境
2. 虚拟显示服务
3. 桌面环境
4. VNC 服务
5. noVNC Web 客户端
6. WebSocket 转发
7. HTTP 反向代理
8. MCP 工具服务
9. token 鉴权
10. 进程守护和健康检查

从零实现容易在浏览器依赖、X11、VNC、进程启动顺序、截图、文件上传、字体、中文输入等细节上消耗大量时间。

### 2.2 AgentScope 已经解决的部分

`/Users/xiehb/workspace/agentscope-runtime` 中已经包含内置镜像源码：

| 能力 | 路径 |
|------|------|
| browser 镜像 | `/Users/xiehb/workspace/agentscope-runtime/src/agentscope_runtime/sandbox/box/browser/Dockerfile` |
| gui 镜像 | `/Users/xiehb/workspace/agentscope-runtime/src/agentscope_runtime/sandbox/box/gui/Dockerfile` |
| base 镜像 | `/Users/xiehb/workspace/agentscope-runtime/src/agentscope_runtime/sandbox/box/base/Dockerfile` |
| filesystem 镜像 | `/Users/xiehb/workspace/agentscope-runtime/src/agentscope_runtime/sandbox/box/filesystem/Dockerfile` |
| MCP 共享路由 | `/Users/xiehb/workspace/agentscope-runtime/src/agentscope_runtime/sandbox/box/shared/routers/mcp.py` |
| builder 入口 | `/Users/xiehb/workspace/agentscope-runtime/src/agentscope_runtime/sandbox/build.py` |
| 自定义镜像示例 | `/Users/xiehb/workspace/agentscope-runtime/examples/sandbox/custom_sandbox` |

本轮验证使用的镜像：

```text
agentscope-registry.ap-southeast-1.cr.aliyuncs.com/agentscope/runtime-sandbox-browser:latest
```

该镜像实际暴露：

1. `/fastapi/healthz`
2. `/fastapi/mcp/list_tools`
3. `/fastapi/mcp/call_tool`
4. `/vnc/vnc_lite.html`
5. `/websockify`

---

## 3. 当前验证链路

当前测试页已经打通的最小闭环：

```text
前端测试页
  -> 后端 /api/sandbox-test/*
    -> docker run 启动 AgentScope browser sandbox
      -> 容器暴露 FastAPI + MCP + VNC/noVNC
        -> 前端 iframe 显示 VNC 桌面
        -> 后端调用 MCP 工具执行浏览器动作
        -> 后端保存截图到 session workspace
```

### 3.1 Docker 启动

后端启动容器时做：

```text
docker run -d
  --name lingzhou-sandbox-test-{sessionId}
  -p {hostPort}:80
  -e SECRET_TOKEN={runtimeToken}
  -v /Users/xiehb/workspace/lingzhou-agent/workspaces/users/{userId}/sessions/{sessionId}:/workspace
  --shm-size=2g
  agentscope-registry.ap-southeast-1.cr.aliyuncs.com/agentscope/runtime-sandbox-browser:latest
```

健康检查：

```text
GET http://127.0.0.1:{hostPort}/fastapi/healthz
Authorization: Bearer {runtimeToken}
```

### 3.2 VNC 显示

前端 iframe 打开：

```text
http://localhost:{hostPort}/vnc/vnc_lite.html?password=<runtime-password>
```

VNC 能力来自镜像内部：

1. `Xvfb :1`
2. `xfce4`
3. `x11vnc :5901`
4. `websockify :9000`
5. `nginx :80`

项目不自己实现 VNC，只消费镜像暴露出来的 noVNC 页面。

### 3.3 MCP 工具调用

后端调用：

```text
POST http://127.0.0.1:{hostPort}/fastapi/mcp/call_tool
Authorization: Bearer {runtimeToken}
Content-Type: application/json
```

请求体：

```json
{
  "tool_name": "browser_navigate",
  "arguments": {
    "url": "https://www.baidu.com"
  }
}
```

当前 browser 镜像实际暴露的是 `playwright` 工具组，包括：

1. `browser_navigate`
2. `browser_snapshot`
3. `browser_click`
4. `browser_type`
5. `browser_select_option`
6. `browser_press_key`
7. `browser_hover`
8. `browser_drag`
9. `browser_wait_for`
10. `browser_take_screenshot`
11. `browser_file_upload`
12. `browser_pdf_save`
13. `browser_network_requests`
14. `browser_console_messages`
15. tab 管理和前进后退等

### 3.4 文件挂载和截图

宿主机 session 目录：

```text
/Users/xiehb/workspace/lingzhou-agent/workspaces/users/{userId}/sessions/{sessionId}
```

容器内目录：

```text
/workspace
```

截图保存：

```text
/Users/xiehb/workspace/lingzhou-agent/workspaces/users/{userId}/sessions/{sessionId}/screenshots/screenshot-YYYYMMDD-HHmmss-SSS.jpg
```

当前截图流程：

1. 调用 `browser_take_screenshot`
2. 容器返回 base64 图片
3. 后端解析图片
4. 保存到 `screenshots/`
5. 前端显示最近截图路径

---

## 4. 后续总体架构

建议分成四层：

```text
前端 GUI / Agent 任务入口
  -> lingzhou-agent 后端会话层
    -> Docker Sandbox Runtime 层
      -> AgentScope Runtime 镜像基座
        -> 内置 MCP + 自研 MCP 工具
```

### 4.1 前端层

职责：

1. 展示云电脑 VNC 画面
2. 展示容器状态、端口、工作目录、截图路径
3. 触发启动、停止、截图、打开 URL、调试工具调用
4. 后续展示 Agent 操作轨迹和任务进度

前端不直接控制 Docker，也不直接持有容器内部 token。

### 4.2 lingzhou-agent 后端层

职责：

1. 创建 sandbox session
2. 分配宿主 workspace
3. 启动和停止 Docker 容器
4. 代理 MCP `list_tools` / `call_tool`
5. 管理 VNC URL
6. 保存截图、日志、运行结果
7. 做权限校验和用户隔离
8. 维护容器心跳和超时清理

### 4.3 Docker Runtime 层

职责：

1. 统一镜像选择
2. 统一环境变量
3. 统一挂载策略
4. 统一资源限制
5. 统一网络策略
6. 统一健康检查
7. 统一容器命名和清理

### 4.4 镜像与 MCP 工具层

职责：

1. 提供浏览器自动化
2. 提供 GUI 桌面能力
3. 提供文件系统能力
4. 提供业务系统专用 MCP 工具
5. 暴露稳定的 MCP 工具 schema

---

## 5. 镜像扩展路线

### 5.1 第一阶段：直接使用 AgentScope browser 镜像

目标：

1. 证明 Docker 启动可控
2. 证明 VNC 能显示
3. 证明 MCP 能调用
4. 证明浏览器表单能自动化
5. 证明截图能落盘
6. 证明 workspace 能挂载

当前阶段已经验证完成。

### 5.2 第二阶段：基于 browser 镜像做自定义构建

基于：

```text
/Users/xiehb/workspace/agentscope-runtime/src/agentscope_runtime/sandbox/box/browser/Dockerfile
```

保留：

1. FastAPI runtime
2. MCP 共享路由
3. Playwright MCP
4. Xvfb / xfce4 / x11vnc / noVNC / websockify / nginx
5. `SECRET_TOKEN` 鉴权

扩展：

1. 自研业务 MCP server
2. 业务系统 SDK / CLI
3. 证书、字体、依赖包
4. 上传下载辅助工具
5. 表单任务封装工具
6. 站点专用操作工具

### 5.3 第三阶段：区分 browser sandbox 与 gui sandbox

`runtime-sandbox-browser` 适合：

1. 网页表单
2. 后台系统
3. 搜索、点击、输入、上传
4. 网页截图
5. 网络请求和 console 观测

`runtime-sandbox-gui` 适合：

1. 更通用的桌面操作
2. 非网页程序
3. 需要鼠标键盘坐标级操作的场景

建议后续同时保留两类沙盒：

```text
browser sandbox
  - 优先用于网页任务
  - 工具更结构化，稳定性更高

gui sandbox
  - 用于桌面级任务
  - 操作更通用，但可解释性和稳定性弱于浏览器工具
```

---

## 6. MCP 扩展策略

### 6.1 不改协议，扩工具

后续自研能力不建议绕过 MCP 单独开协议，优先继续使用：

```text
/mcp/list_tools
/mcp/call_tool
```

好处：

1. Agent 工具发现逻辑统一
2. 前端调试入口统一
3. 后端代理实现统一
4. 后续可替换镜像基座
5. 工具调用记录和审计统一

### 6.2 工具分层

建议工具分三类：

#### 通用浏览器工具

来自 Playwright MCP：

1. 打开页面
2. 获取 snapshot
3. 点击
4. 输入
5. 下拉选择
6. 上传文件
7. 截图
8. 等待结果

#### 通用系统工具

可后续补：

1. 文件列表
2. 文件读取
3. 文件写入
4. 命令执行
5. 压缩解压
6. 下载产物

#### 业务 MCP 工具

由本项目扩展：

1. 低代码平台登录状态检查
2. 表单字段批量填写
3. 表单提交并确认
4. 业务对象搜索
5. 页面结构解析
6. 业务系统文件上传
7. 特定流程的一键操作

### 6.3 表单提交能力

网页表单优先使用 browser 工具实现：

```text
browser_navigate
browser_snapshot
browser_type
browser_select_option
browser_click
browser_wait_for
browser_take_screenshot
browser_network_requests
```

如果某个业务系统表单很复杂，再封装业务 MCP 工具，例如：

```json
{
  "tool_name": "business_form_fill_and_submit",
  "arguments": {
    "formCode": "expense",
    "fields": {
      "title": "差旅报销",
      "amount": "1000"
    }
  }
}
```

---

## 7. lingzhou-agent 后端落地模块建议

建议从当前测试代码演进为正式模块：

### 7.1 `SandboxSessionService`

职责：

1. `startSession`
2. `getSession`
3. `stopSession`
4. `heartbeat`
5. `cleanupExpiredSessions`
6. `recoverOrphanContainers`

维护字段：

1. `sessionId`
2. `userId`
3. `containerId`
4. `containerName`
5. `imageName`
6. `runtimeToken`
7. `hostPort`
8. `workspacePath`
9. `status`
10. `lastAccessAt`
11. `createdAt`

### 7.2 `SandboxToolService`

职责：

1. `listTools`
2. `callTool`
3. `callBrowserTool`
4. `saveScreenshot`
5. `savePdf`
6. `collectNetworkRequests`
7. `collectConsoleMessages`

### 7.3 `SandboxWorkspaceService`

职责：

1. 创建用户 session 目录
2. 创建 `screenshots/`
3. 创建 `uploads/`
4. 创建 `outputs/`
5. 管理宿主路径到容器路径映射
6. 防止路径逃逸

### 7.4 `AgentComputerExecutor`

职责：

1. 接收 Agent 规划
2. 调用 `browser_snapshot` 观察页面
3. 根据页面 ref 执行 click/type/select
4. 等待结果
5. 失败时截图和记录网络请求
6. 输出结构化执行轨迹

---

## 8. 容器生命周期

当前测试阶段只有手动停止：

```text
POST /api/sandbox-test/{sessionId}/stop
  -> docker rm -f {containerName}
```

正式实现需要补齐：

### 8.1 心跳

前端和 Agent 执行器定期调用：

```text
POST /api/sandbox/{sessionId}/heartbeat
```

后端更新：

```text
lastAccessAt = now
```

### 8.2 空闲回收

定时任务扫描：

```text
now - lastAccessAt > idleTtl
```

命中后：

```text
docker rm -f {containerName}
status = EXPIRED
```

### 8.3 启动恢复和孤儿清理

后端启动时扫描：

```text
docker ps -a --filter name=lingzhou-sandbox-
```

对无法恢复到数据库记录的容器执行清理。

### 8.4 资源限制

正式环境建议加：

```text
--memory
--cpus
--pids-limit
--security-opt
--cap-drop
--network
```

---

## 9. 工作区目录策略

本项目继续使用自己的 workspace 语义，不照搬 AgentScope 的目录模型。

推荐宿主目录：

```text
/Users/xiehb/workspace/lingzhou-agent/workspaces/users/{userId}/sessions/{sessionId}
```

后续可细化：

```text
sessions/{sessionId}/
  workspace/
  uploads/
  outputs/
  screenshots/
  logs/
  meta/
```

容器内推荐映射：

```text
/workspace
/uploads
/outputs
/screenshots
/logs
```

当前测试阶段先把整个 session 目录挂到：

```text
/workspace
```

正式阶段可以改为多目录精细挂载。

---

## 10. 与 Agent 的关系

Docker GUI 云电脑不直接等于 Agent。

它只是 Agent 的一个执行环境：

```text
Agent 决策
  -> 工具选择
    -> browser/gui MCP 工具调用
      -> 容器内执行
        -> VNC 可视化
        -> 结果回传
```

Agent 层应关注：

1. 任务理解
2. 页面观察
3. 下一步动作选择
4. 失败重试
5. 结果确认
6. 轨迹总结

Sandbox 层只关注：

1. 容器是否可用
2. 工具是否可调用
3. 文件是否正确挂载
4. 结果是否保存
5. 生命周期是否安全

两层不要混在一起。

---

## 11. 推荐演进阶段

### 阶段 1：测试闭环

已完成：

1. Docker 启动
2. VNC 显示
3. 打开百度
4. MCP 工具调用
5. 截图保存
6. workspace 挂载

### 阶段 2：正式 sandbox session 管理

需要完成：

1. session 持久化
2. 心跳
3. 超时回收
4. 孤儿容器清理
5. 容器资源限制
6. 工具调用审计

### 阶段 3：Agent 浏览器执行器

需要完成：

1. `browser_snapshot` 观察
2. ref 级点击和输入
3. 表单填写和提交
4. 错误截图
5. 网络请求分析
6. 执行轨迹结构化

### 阶段 4：自定义镜像与业务 MCP

需要完成：

1. 基于 AgentScope browser Dockerfile 构建自有镜像
2. 添加自研 MCP server
3. 添加业务系统专用工具
4. 添加必要依赖、证书、字体、CLI
5. 制定镜像版本发布策略

### 阶段 5：GUI 云电脑增强

需要完成：

1. 引入或扩展 `runtime-sandbox-gui`
2. 支持 desktop 级 computer use
3. 支持非网页应用
4. 建立 GUI 动作审计和截图轨迹

---

## 12. 风险与注意事项

### 12.1 不要过早重写镜像底层

VNC、Xvfb、noVNC、nginx、supervisor 的组合已经在 AgentScope 中跑通。

后续优先扩 MCP 工具和业务编排，避免把时间消耗在底层进程稳定性上。

### 12.2 browser 工具优先于 GUI 坐标操作

网页任务优先用 Playwright 工具：

1. 可解释性更强
2. 可重试性更好
3. 元素 ref 更稳定
4. 不依赖屏幕坐标

只有网页工具无法处理时，再退到 GUI / computer use。

### 12.3 token 不能泄露

`SECRET_TOKEN` 同时保护：

1. FastAPI MCP 接口
2. VNC 密码

后端日志、前端 UI、错误信息都不应直接暴露 token。

### 12.4 需要容器清理机制

如果没有心跳和超时回收，用户关闭页面后容器会继续运行。

正式上线前必须补：

1. idle TTL
2. heartbeat
3. backend restart cleanup
4. admin 手动清理入口

---

## 13. 当前建议

下一步不要直接大改镜像，而是按顺序做：

1. 把当前测试代码收敛成正式 `sandbox` 模块
2. 增加 session 持久化和容器生命周期
3. 增加通用 `listTools` / `callTool` API
4. 增加 Agent 浏览器执行器
5. 跑通真实业务表单填写和提交
6. 再基于 AgentScope browser Dockerfile 构建自有镜像
7. 最后沉淀业务 MCP server

一句话：

> **AgentScope 做云电脑与 MCP 基座，lingzhou-agent 做业务会话和 Agent 编排，自研能力通过 MCP 工具逐步长出来。**
