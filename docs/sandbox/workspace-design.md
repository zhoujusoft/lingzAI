# 工作区设计

## 设计目标

工作区设计需要同时满足以下目标：

- 技能工坊和运行时执行彻底分离
- skill 执行按 `用户 + 会话` 隔离
- skill 定义目录默认只读
- 文件工具、脚本工具只能在允许范围内操作
- 后续可平滑升级到 Docker / Kubernetes / 更强沙盒
- 用户长期偏好、助理记忆可沉淀为 `md` 文件

## 总体分层

整个系统分成 4 个区：

1. 公共工坊区
   - 存技能工坊草稿、模板、记忆、编排产物
2. 用户长期资料区
   - 存用户偏好、助理个性、长期记忆
3. 用户会话工作区
   - 存某次 skill 运行的输入、中间文件、输出、日志
4. skill 定义区
   - 存正式 skill 定义
   - 默认只读

## 完整目录模型

```text
/workspaces/
├── public/
│   ├── skillstudio/
│   │   ├── draft/
│   │   │   └── <skill-name>/
│   │   │       ├── SKILL.md
│   │   │       ├── references/
│   │   │       └── scripts/
│   │   ├── skills/
│   │   │   └── zhuoju-skill-creator/
│   │   │       ├── SKILL.md
│   │   │       └── templates/
│   │   │           ├── structure/
│   │   │           └── capability/
│   │   ├── templates/
│   │   ├── memory/
│   │   ├── logs/
│   │   ├── protocols/
│   │   ├── architecture/
│   │   └── intent-map.json
│   │
│   ├── skills/
│   │   └── <skill-name>/
│   │       ├── SKILL.md
│   │       ├── references/
│   │       │   └── ...
│   │       ├── scripts/
│   │       │   └── ...
│   │       ├── requirements.txt
│   │       ├── skill.env
│   │       └── meta/
│   │           ├── manifest.json
│   │           ├── tool-policy.json
│   │           └── runtime.json
│   │
│   ├── runtime-envs/
│   │   ├── python/
│   │   │   ├── default/
│   │   │   │   ├── requirements.txt
│   │   │   │   ├── manifest.json
│   │   │   │   └── .venv/
│   │   │   └── skills/
│   │   │       └── <skill-name>/
│   │   │           ├── requirements.lock.txt
│   │   │           ├── manifest.json
│   │   │           ├── install.log
│   │   │           └── .venv/
│   │   └── caches/
│   │       ├── pip/
│   │       └── wheels/
│   │
│   └── logs/
│       ├── runtime/
│       └── env/
│
└── users/
    └── <userId>/
        ├── profile/
        │   ├── preferences.md
        │   ├── assistant-style.md
        │   ├── memory.md
        │   └── agent/
        │       ├── prompt.md
        │       ├── memory.md
        │       └── preferences.md
        │
        └── sessions/
            └── <sessionId>/
                ├── workspace/
                ├── uploads/
                ├── outputs/
                ├── temp/
                ├── logs/
                └── meta/
                    ├── session.json
                    ├── artifacts.json
                    └── run-state.json
```

## 各区域职责

### 1. `/workspaces/public/skillstudio/`

这是公共技能工坊区。

职责：

- skill 草稿
- skill 模板
- 工坊 memory
- `zhuoju-skill-creator` 的编排产物
- 工坊调试日志

特点：

- 系统级共享资源
- 不属于某个运行会话
- 有权限控制
- 不作为 skill 运行时目录

### 2. `/workspaces/public/skills/`

这是正式 skill 定义区。

职责：

- `SKILL.md`
- `references/`
- `scripts/`
- `requirements.txt`
- `skill.env`
- `meta/`

特点：

- 放正式发布的 skill
- 运行时默认只读
- 不允许 skill 运行时直接修改自己

### 3. `/workspaces/public/runtime-envs/`

这是运行环境区。

职责：

- 存放公共 Python 运行环境
- 存放按 skill 单独构建的 `.venv`
- 存放 pip / wheels 缓存

当前阶段平台规范：

- 平台统一 Python 主版本为 `3.11`
- native runtime 当前不做多 Python 主版本并存
- skill 的 wheel、离线依赖、环境缓存统一按 `cp311` 口径准备

设计原则：

- `requirements.txt` 属于 skill 定义层
- `.venv` 属于运行环境层
- 不把 `.venv` 直接放进 skill 定义目录
- 当前阶段先初始化目录与默认 `requirements.txt`
- 当前阶段所有 skill 默认进入 `native runtime`
- 当前阶段不做自动复制 skill 到会话工作区
- `public/skills/` 是 skill 的公共事实源
- Python 环境与 `.venv` 规则见 `docs/sandbox/runtime-python-env-design.md`

### 4. `/workspaces/users/<userId>/profile/`

这是用户长期资料区。

职责：

- 用户偏好
- 助理风格
- 长期记忆
- 企业用户级个人 Agent 长期配置

建议文件：

- `preferences.md`
- `assistant-style.md`
- `memory.md`
- `agent/prompt.md`
- `agent/memory.md`
- `agent/preferences.md`

### 5. `/workspaces/users/<userId>/sessions/<sessionId>/`

这是运行时会话区。

职责：

- 当前 skill 运行的全部文件活动范围

子目录职责：

- `workspace/`
  - 主工作目录
  - skill 中间文件默认写这里
- `uploads/`
  - 用户上传文件
  - 默认只读
- `outputs/`
  - 最终输出文件
  - 适合导出前暂存
- `temp/`
  - 临时文件
- `logs/`
  - 运行日志
- `meta/`
  - 当前运行元数据
  - 后续逐步补充 `session.json`、`artifacts.json`、`run-state.json`

## 两条主要链路

### A. 技能工坊链路

路径作用域：

- 公共工坊区可读写
- skill 定义区可读
- 用户会话区不参与

流程：

1. 用户提出创建或编辑 skill 需求
2. 工坊上下文工程组织上下文
3. 调用 `zhuoju-skill-creator`
4. 生成草稿到 `public/skillstudio/draft/`
5. 用户确认
6. 发布到 `public/skills/`

### B. skill 运行时链路

路径作用域：

- 当前用户会话区可读写
- skill 定义区只读
- 公共工坊区不可写

流程：

1. 用户在某个会话触发 skill
2. runtime 分配 `userId + sessionId` 工作区
3. 运行 skill
4. 中间文件写 `workspace/` 或 `temp/`
5. 最终结果写 `outputs/` 或直接 `writeArtifact`
6. 返回下载链接或结果摘要

## 工具作用域规则

### `readFile`

可读：

- 当前会话区
- 当前用户 `profile/`
- skill 定义区
- 必要的公共模板区

不可读：

- 其他用户目录
- 项目根目录任意路径

### `writeFile`

可写：

- 当前会话 `workspace/`
- 当前会话 `temp/`
- 当前会话 `logs/`
- 工坊链路下可写公共工坊草稿区

不可写：

- skill 定义区
- 项目根目录
- 其他用户目录

### `writeArtifact`

可写：

- 当前会话 `outputs/`
- 然后上传对象存储

返回：

- `id`
- `fileName`
- `downloadUrl`

### `runPython`

工作目录固定：

- 当前会话 `workspace/`

允许读写：

- 当前会话区
- skill 定义区只读资源

不可写：

- skill 定义区
- 公共工坊区
- 项目根目录

补充规范：

- 统一通过 `runtime_tool(action="run_python")` 调用
- 统一运行在平台受控 Python 环境中
- 当前平台 Python 版本规范固定为 `3.11`
- 不允许 skill 在运行时自行切换解释器版本

## 当前实现状态

截至 2026-04-27，当前代码已经落地为：

- `workspaces/public/skills/` 作为正式 skill 定义区
- `workspaces/public/skillstudio/` 作为技能工坊公共区
- 新建用户时自动初始化 `profile/` 与基础 `.md` 文件
- runtime 首次解析会话时自动创建 `workspace/`、`uploads/`、`outputs/`、`temp/`、`logs/`、`meta/`
- skill 运行时直接只读挂载 `public/skills/<skill-name>/`，不复制 skill
- `native runtime` 为当前默认执行环境

新增口径：

- Python 运行环境默认由 `public/runtime-envs/python/` 承载
- 带 `requirements.txt` 的 skill 优先使用独立 `.venv`
- 当前平台标准 Python 版本统一为 `3.11`

## 隔离规则

第一版先靠：

- 目录隔离
- 路径校验
- 工具白名单
- 只读 / 可写边界

也就是：

- 先不强依赖容器
- 先让所有工具都带“当前作用域”

后续升级时：

- 本地沙盒
- Docker
- Kubernetes

都只是在执行层替换，不改这套目录契约。

## 作用域模型

建议 runtime 每次执行时，都构造一个明确作用域：

```text
scopeType: runtime | studio
userId: <userId>
sessionId: <sessionId>
skillName: <skill-name>
allowedReadRoots: [...]
allowedWriteRoots: [...]
```

这样所有工具都统一走作用域校验。

## 设计原则总结

1. 技能工坊写公共工坊区
2. skill 运行写当前会话区
3. 用户长期偏好写用户资料区
4. skill 定义区默认只读
5. 所有工具都必须在作用域内工作

## 一句话版本

公共工坊区负责创作，用户资料区负责长期记忆，用户会话区负责运行隔离，skill 定义区负责只读能力定义；所有工具都必须在作用域内工作。
