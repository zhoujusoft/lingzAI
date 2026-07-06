# Runtime Python 执行与环境设计

**版本**: v2.0  
**日期**: 2026-04-28  
**状态**: 方案落地  
**适用范围**: Lingz Agent Runtime 在 `native` 模式下的 Python 执行、依赖安装、`.venv` 缓存与离线运行

---

## 1. 目标

本设计解决以下问题：

1. 在去掉开放式 `bash` 后，如何保留受控的脚本执行能力
2. skill 自带 `scripts/`、`requirements.txt`、离线依赖包时，如何稳定执行
3. `.venv` 放在哪里，按什么粒度缓存
4. 服务端部署时需要预装哪些 Python 基础能力
5. 如何支持“尽量离线可运行”的 skill 交付模型

本设计建立在以下前提上：

1. 当前阶段 runtime 工具应收紧，不能再把宿主 shell 当作通用能力暴露
2. skill 定义目录位于 `workspaces/public/skills/`
3. skill 定义目录运行时只读
4. session 工作区负责输入、输出、中间文件，不承载依赖安装产物
5. Python 执行能力最终通过 `runtime_tool(action="run_python")` 暴露，而不是 `bash`

---

## 2. 结论先行

Lingz Runtime 的 Python 能力收敛为：

1. 平台部署时统一预装 **Python 3.11**、`pip`、`venv`
2. skill 可以自带：
   - `scripts/`
   - `requirements.txt`
   - `vendor/` 或 `wheels/` 离线依赖包目录
3. runtime 为 skill 构建并缓存 `.venv`
4. 模型只能通过 `runtime_tool(action="run_python")` 运行脚本
5. 不再向模型暴露 `bash`

当前阶段再加一条硬约束：

1. 平台 Python 主版本固定为 `3.11`
2. native runtime 当前不做多 Python 主版本并存
3. skill 不声明自定义 Python 主版本
4. 离线 wheel、环境缓存、运行验证统一按 `cp311` 口径准备

一句话：

> **Python 是 runtime 的受控能力，不是开放命令行能力。**

---

## 3. 基本原则

### 3.1 skill 声明依赖，runtime 构建环境

`requirements.txt` 属于 skill 定义层，和以下内容同层：

1. `SKILL.md`
2. `scripts/`
3. `references/`
4. `vendor/` 或 `wheels/`

而 `.venv` 属于 runtime 环境层，不属于 skill 内容本身。

### 3.2 不把完整 `.venv` 打进 skill 包

第一版不把完整 `.venv` 作为 skill 发布物的一部分。

原因：

1. 体积太大
2. 强依赖操作系统与 Python 小版本
3. 在 native / docker / remote 三种 backend 下兼容性差
4. 不利于 skill 包审计与发布

因此推荐方案是：

1. skill 自带 `requirements.txt`
2. skill 可选自带离线 wheel 包
3. runtime 在部署环境中构建 `.venv`
4. 后续复用该 `.venv`

### 3.3 session 与 Python 环境解耦

session 工作区很多，但 Python 环境不应为每个 session 单独构建。

推荐关系：

1. 一个 skill 对应一个稳定的 Python 环境缓存
2. 多个 session 共享该 skill 的 `.venv`
3. session 目录只保存输入、输出、中间文件和日志

### 3.4 Python 执行必须是受控动作

模型不允许自行拼接 shell 命令来执行 Python。

必须使用：

```json
{
  "action": "run_python",
  "params": {
    "scriptPath": "/skill/scripts/main.py",
    "args": ["--input", "/uploads/a.docx", "--output", "/outputs/b.docx"],
    "workDir": "/workspace",
    "timeoutSeconds": 120
  }
}
```

不允许继续依赖：

1. `bash("python3 ...")`
2. `bash("pip install ...")`
3. 任意解释器路径拼接

### 3.5 Python 版本由平台统一控制

当前阶段的版本策略直接定死：

1. 平台标准 Python 版本为 `3.11`
2. native runtime 不支持按 skill 选择 `3.8`、`3.10`、`3.11` 等不同主版本解释器
3. skill 可以声明 Python 包依赖，但不能声明“必须切换到其他 Python 主版本”
4. 若后续出现历史 skill 与 `3.11` 不兼容，应单独做兼容方案，而不是回退为多版本长期共存

这样做的目标是：

1. 让部署环境可控
2. 让 `vendor/` 下载标准统一
3. 让 `.venv` 缓存命中稳定
4. 为后续 docker / remote runtime 产品化打基础

---

## 4. 服务端预装能力

Lingz 服务端部署环境至少需要具备以下基础能力：

1. `python3.11`
2. `python3.11 -m venv`
3. `pip`
4. 基础 CA 证书与压缩解压能力

推荐约定：

1. `python3` 指向平台标准 `Python 3.11`
2. runtime 配置中的 Python 命令默认也应指向 `3.11`
3. native runtime 创建 `.venv` 时统一基于这套解释器

推荐平台默认预装少量通用包，但不要求覆盖所有 skill：

1. `requests`
2. `pydantic`
3. `python-docx`
4. `openpyxl`
5. `PyYAML`

注意：

1. 这些是平台默认能力，不等于 skill 可以省略依赖声明
2. skill 仍应通过 `requirements.txt` 明确自己真正依赖的包
3. 这样在迁移到 docker / remote 时仍能保持环境可复现

补充边界：

1. 平台预装包是冷启动优化和运行兜底，不是省略 `requirements.txt` 的理由
2. 需要完全离线交付时，skill 仍应提供自己的 `vendor/` wheel 包
3. native runtime 当前可继承平台预装 site-packages，但这属于运行时实现细节，不属于 skill 可依赖的隐式契约

---

## 5. 目录模型

```text
/workspaces/public/
├── skills/
│   └── <skill-name>/
│       ├── SKILL.md
│       ├── scripts/
│       ├── references/
│       ├── requirements.txt
│       └── vendor/
│           └── *.whl
│
└── runtime-envs/
    └── python/
        ├── default/
        │   ├── manifest.json
        │   ├── install.log
        │   └── .venv/
        │
        ├── general-code/
        │   ├── requirements.txt
        │   └── vendor/
        │
        └── skills/
            └── <skill-name>/
                ├── requirements.source.txt
                ├── requirements.lock.txt
                ├── manifest.json
                ├── install.log
                └── .venv/

/workspaces/users/
└── <user-id>/
    └── runtime-envs/
        └── python/
            └── general-code/
                ├── requirements.source.txt
                ├── requirements.lock.txt
                ├── manifest.json
                ├── install.log
                └── .venv/
```

说明：

### `skills/<skill-name>/`

skill 定义目录内保存：

1. `SKILL.md`
2. `scripts/`
3. `references/`
4. `requirements.txt`
5. 离线依赖目录，如 `vendor/`

### `runtime-envs/python/default/`

平台默认 Python 环境。

职责：

1. 提供基础 Python 解释器与少量平台级依赖
2. 作为没有 `requirements.txt` 的 skill 默认环境

### `runtime-envs/python/general-code/`

个人 Agent 通用 CODE 环境。

职责：

1. 服务于 general world 下的 `/workspace/*.py`
2. 提供高频通用数据处理与文档产出依赖
3. 与 skill 专属环境解耦，避免“借 skill 环境跑通用代码”
4. 仅保存共享 `requirements.txt` 与可选 `vendor/`

### `users/<user-id>/runtime-envs/python/general-code/`

通用 CODE 环境的用户级运行时目录。

职责：

1. 保存当前用户自己的 `.venv`
2. 保存当前用户自己的安装日志与 `manifest.json`
3. 避免不同用户共享同一套 `general-code` 运行时状态

### `runtime-envs/python/skills/<skill-name>/`

skill 独立 Python 环境缓存目录。

职责：

1. 保存该 skill 的依赖快照
2. 保存安装日志与状态
3. 保存该 skill 的独立 `.venv`

---

## 6. 何时使用默认环境，何时使用独立环境

### 6.1 默认环境

以下情况使用 `runtime-envs/python/default/.venv`：

1. skill 没有 `requirements.txt`
2. skill 只依赖平台默认预装能力
3. skill 只是轻量脚本，不需要独立版本隔离

以下情况使用 `users/<user-id>/runtime-envs/python/general-code/.venv`：

1. 个人 Agent 在 general world 中执行 `/workspace/*.py`
2. 当前任务属于通用 CODE 升级路径，而不是 skill 内部脚本
3. 需要使用平台预装的通用数据/文档处理能力

### 6.1.1 `general-code` 离线安装

若以下目录存在 wheel 包：

```text
workspaces/public/runtime-envs/python/general-code/vendor/
```

则优先使用：

```text
pip install --no-index --find-links <vendor> -r requirements.txt
```

这使得 `general-code` 可以提前打成离线安装包，而不是依赖在线拉取。

同时需要强调：

1. `requirements.txt` 与 `vendor/` 是全局共享定义
2. `.venv`、`manifest.json`、`install.log` 按用户隔离
3. 首次命中 `/workspace/*.py` 时，runtime 会为当前用户按需构建自己的 `general-code` 环境

### 6.2 独立环境

以下情况使用 `runtime-envs/python/skills/<skill-name>/.venv`：

1. skill 存在 `requirements.txt`
2. skill 依赖第三方 Python 包
3. skill 依赖版本与其他 skill 有冲突风险
4. skill 需要可复现的独立运行环境

### 6.3 当前阶段推荐规则

当前阶段建议采用最简单规则：

1. 只要 skill 存在 `requirements.txt`，就使用独立环境
2. 没有 `requirements.txt` 的 skill，统一走默认环境

这样做的好处是：

1. 规则简单
2. 易于排查
3. 不需要猜测 skill 是否“真的需要”独立环境

### 6.4 当前阶段明确不支持

以下能力当前阶段不支持：

1. 在 native runtime 中长期并存多套 Python 主版本
2. skill 元数据里声明 `pythonVersion=3.8/3.10/3.12` 并要求 runtime 自动切换
3. 模型在执行时自行安装解释器、切换解释器或拼 shell 命令运行 Python

这些能力如果后续要做，只能在 docker / remote runtime 产品化阶段重新设计。

---

## 7. 离线依赖策略

### 7.1 推荐支持的 skill 依赖形态

第一版推荐支持：

1. `requirements.txt`
2. `vendor/*.whl`

即：

1. 在线环境可直接按 `requirements.txt` 安装
2. 离线环境优先从 `vendor/` 安装 wheel 包

### 7.2 运行时安装策略

如果 skill 目录下存在 `vendor/`，优先使用：

```bash
pip install --no-index --find-links <skill>/vendor -r requirements.txt
```

如果不存在 `vendor/`，则允许平台在可联网环境下按常规方式安装。

### 7.3 产品口径

对 skill 作者的推荐约定应明确为：

1. 有网络的部署环境，可仅提供 `requirements.txt`
2. 需要离线交付时，skill 应同时附带 `vendor/` wheel 包目录

这就能达到你想要的“离线使用”效果，而不必把完整 `.venv` 打进 skill 包。

### 7.4 wheel 规范

为了让环境可控，当前建议把 wheel 口径直接统一为：

1. 优先准备 `cp311` 对应 wheel
2. 优先准备与目标部署平台匹配的二进制 wheel，而不是源码包
3. 除非确认依赖是纯 Python 包，否则不要只放 `tar.gz`
4. 若 skill 要求完全离线可运行，应把关键依赖 wheel 一并放入 `vendor/`

推荐下载示例：

```bash
python3.11 -m pip download \
  --only-binary=:all: \
  --dest vendor \
  --python-version 311 \
  --implementation cp \
  --abi cp311 \
  <package-name>==<version>
```

---

## 8. 环境生命周期

### 8.1 创建时机

不在“创建 session”时创建 Python 环境。

推荐创建时机：

1. skill 首次调用 `runtime_tool(action="run_python")`
2. skill 发布时后台预热环境
3. 管理员显式触发“重建 skill Python 环境”

### 8.2 复用规则

以下条件同时满足时直接复用 `.venv`：

1. `.venv/` 存在
2. `manifest.json` 存在
3. `requirements.txt` 指纹未变化
4. 上次安装状态为成功

### 8.3 失效重建

以下情况触发重建：

1. `requirements.txt` 内容变化
2. `.venv` 不存在
3. `manifest.json` 不存在
4. 上次安装失败
5. 平台 Python 版本变化
6. 管理员显式要求重建

对 `general-code` 而言，上述判定发生在用户目录下，即：

1. 检查 `users/<user-id>/runtime-envs/python/general-code/.venv`
2. 对比共享 `workspaces/public/runtime-envs/python/general-code/requirements.txt`
3. 写回用户自己的 `manifest.json` 与 `install.log`

---

## 9. 元数据与指纹

每个独立环境目录下保存 `manifest.json`：

```json
{
  "skillName": "docx-translation-pairs",
  "pythonVersion": "3.11.x",
  "requirementsSha256": "xxxx",
  "requirementsSourcePath": "/workspaces/public/skills/docx-translation-pairs/requirements.txt",
  "vendorSha256": "yyyy",
  "venvPath": "/workspaces/public/runtime-envs/python/skills/docx-translation-pairs/.venv",
  "installedAt": "2026-04-28T10:00:00+08:00",
  "installStatus": "SUCCESS"
}
```

关键字段：

1. `requirementsSha256`
2. `vendorSha256`
3. `pythonVersion`
4. `installStatus`

其中最关键的是：

1. `requirementsSha256`
2. `vendorSha256`

它们决定环境是否需要重建。

---

## 10. `run_python` 协议定义

### 10.1 对模型暴露的统一动作

最终不单独暴露 `runPython` 工具名，而是继续通过：

```json
{
  "tool": "runtime_tool",
  "action": "run_python",
  "params": {
    "scriptPath": "/skill/scripts/main.py",
    "args": [],
    "workDir": "/workspace",
    "timeoutSeconds": 120
  }
}
```

### 10.2 参数约束

`run_python` 建议支持如下参数：

1. `scriptPath`
   - 必填
   - 只能指向 `/skill/scripts/**` 或 `/workspace/**`

2. `args`
   - 可选
   - 必须是字符串数组
   - 不允许传整段 shell 命令

3. `workDir`
   - 可选
   - 默认 `/workspace`

4. `timeoutSeconds`
   - 可选
   - 默认 120
   - 需要平台上限保护

5. `env`
   - 第一版建议不对模型开放
   - 避免模型自行注入环境变量破坏可控性

补充约束：

1. `run_python` 的职责是执行受控脚本，不是开放式 Python REPL
2. 不允许直接执行任意代码字符串
3. 不允许把 `run_python` 退化成 shell 包装器
4. `run_python` 不负责安装解释器或动态切换 Python 主版本

### 10.3 解释器选择顺序

执行 `run_python` 时，runtime 按脚本来源选择环境：

1. `/workspace/*.py` -> `users/<user-id>/runtime-envs/python/general-code/.venv/bin/python`
2. `/skill/scripts/*.py` 且 skill 有 `requirements.txt` -> `runtime-envs/python/skills/<skill-name>/.venv/bin/python`
3. `/skill/scripts/*.py` 且 skill 无 `requirements.txt` -> `runtime-envs/python/default/.venv/bin/python`
4. 若对应 `.venv` 尚未就绪，则先按规则构建，再执行

一句话：

> **`/workspace` 走用户隔离的 `general-code`，skill 脚本走 skill/default 环境；是否命中哪个环境由脚本来源决定。**

补充说明：

1. 默认 `.venv`、用户隔离的 `general-code` `.venv`、skill 独立 `.venv` 都建立在平台 `Python 3.11` 之上
2. 当前阶段不支持根据 skill 动态切换到其他 Python 主版本

---

## 11. 与工作区的关系

session 工作区与 Python 环境的职责划分如下：

### session 工作区负责

1. `/uploads`
2. `/workspace`
3. `/outputs`
4. `/temp`
5. `/logs`

### Python 环境缓存负责

1. `.venv`
2. 依赖安装状态
3. requirements 指纹
4. 离线包安装日志

因此：

1. 不把 `.venv` 放到 session 工作区
2. 不把 `.venv` 放到 skill 定义目录
3. `.venv` 应作为公共 runtime 缓存存在

---

## 12. 推荐的 skill 目录约定

推荐 skill 作者按以下结构组织：

```text
<skill-name>/
├── SKILL.md
├── scripts/
│   └── main.py
├── references/
├── requirements.txt
└── vendor/
    ├── requests-*.whl
    └── python_docx-*.whl
```

约定：

1. `SKILL.md` 负责说明何时调用脚本
2. `scripts/` 放可执行 Python 代码
3. `requirements.txt` 声明运行依赖
4. `vendor/` 可选，用于离线交付
5. 若提供 wheel，默认按 `cp311` 口径准备

---

## 13. 当前阶段建议的 runtime 工具边界

当前阶段 runtime 工具白名单建议为：

1. `file_read`
2. `file_write`
3. `list_dir`
4. `stat`
5. `write_artifact`
6. 后续新增 `run_python`

当前阶段不建议保留：

1. `bash`
2. `search`

原因：

1. `bash + file_write` 组合开放性过大
2. `search` 会鼓励模型做开放式探索，而不是按受控文件协议执行
3. Python 执行应被收敛到 `run_python`，而不是依赖 shell

---

## 14. 落地顺序

建议按以下顺序实施：

### Phase 1

1. 从 `runtime_tool` 中移除 `bash`
2. 保留：
   - `file_read`
   - `file_write`
   - `list_dir`
   - `stat`
   - `write_artifact`

### Phase 2

1. 新增 `run_python`
2. 新增 Python 解释器选择逻辑
3. 新增 `requirements.txt` 指纹检查

### Phase 3

1. 支持 `vendor/` 离线依赖安装
2. 完善 skill 发布时环境预热
3. 增加后台 Python 环境管理能力

---

## 15. 最终结论

Lingz Runtime 在 Python 执行上的推荐模型是：

1. 平台统一预装 `Python 3.11` / `pip` / `venv`
2. skill 自带 `scripts/`、`requirements.txt`、可选离线依赖包
3. runtime 为 skill 构建并缓存 `.venv`
4. 模型通过 `runtime_tool(action="run_python")` 调用脚本
5. 不再暴露 `bash`

这条路线既能支持离线使用，也更适合后续统一到 `native / docker / remote` 三种 runtime backend。
