# Lingzhou Agent

[English Documentation](./README.md)

Lingzhou Agent 是一个基于 Spring Boot、Spring AI、Vue 3 与 Docker Compose 构建的开源 AI 应用工程。
它将聊天、技能感知工具编排、知识库 RAG、数据集管理和集成能力放在同一个仓库中，方便本地开发、二次集成和私有化部署。

当前仓库主要包含：

- `core`：可复用的技能感知扩展模块
- `backend`：Spring Boot 后端，承载聊天、知识库、数据集、工具发布与集成能力
- `frontend`：基于 Vue 3 的前端工作区
- `deploy`：Docker Compose、镜像构建、环境模板与部署脚本

## 核心特性

- 基于 Spring AI 的技能感知聊天能力
- 基于文件系统的技能包加载与运行时工具注入
- 知识库文档上传、切片、检索、问答链路
- 数据集管理与数据集发布为工具
- 预留 MCP 与外部平台集成扩展点
- 提供 Docker Quick 模式，便于快速体验和单机部署

## 仓库结构

```text
.
├── backend/                # Spring Boot 应用
├── core/                   # 可复用的技能扩展核心模块
├── frontend/               # pnpm workspace
│   ├── packages/core/      # 前端共享能力
│   └── packages/web/       # Vue 3 + Vite Web 应用
├── deploy/                 # Docker Compose、镜像、环境模板、辅助脚本
├── docs/                   # 设计文档与接口说明
├── pom.xml                 # Maven 父工程
├── README.md
└── README_zh.md
```

## 技术栈

- 后端：Java 17、Spring Boot 3.4、Spring AI、MyBatis-Plus、Redis、MinIO、Elasticsearch
- 前端：Vue 3、Vite、pnpm workspace、Tailwind CSS
- 部署：Docker Compose

## 快速开始

### 方案 A：使用 Docker Compose 快速体验

这是最适合第一次启动项目的方式。

1. 复制 Quick 部署环境模板：

```bash
cp deploy/compose-quick/.env.example deploy/compose-quick/.env
```

2. 编辑 `deploy/compose-quick/.env`，至少填写以下模型相关配置：

- `APP_CHAT_QWEN_ONLINE_API_KEY`
- `APP_EMBEDDING_API_KEY`
- `APP_RAG_RERANK_API_KEY`

3. 启动整套服务：

```bash
./deploy/manage.sh quick-up
```

默认情况下：

- 前端端口为 `80`
- 后端 API 上下文路径为 `/api`

相关文档：

- `deploy/README.md`
- `deploy/compose-quick/README.md`

### 方案 B：本地开发模式

如果你希望中间件走 Docker、本地直接运行前后端进程，可以使用这个流程。

1. 启动开发用中间件：

```bash
cp deploy/.env.dev.example deploy/.env.dev
./deploy/manage.sh dev-up
```

该流程会准备并启动：

- MySQL：`3306`
- Redis：`16379`
- MinIO：`19000`、`19001`
- Elasticsearch：`9200`

2. 在仓库根目录启动后端：

```bash
mvn -f backend/pom.xml spring-boot:run
```

后端默认信息：

- 基础地址：`http://localhost:5050`
- API 上下文路径：`/api`

3. 启动前端：

```bash
cd frontend
pnpm install
pnpm dev
```

前端开发服务器默认地址为 `http://localhost:5173`，并将 `/api` 代理到后端。

更多说明见 `deploy/README.dev.md`。

## 主要功能域

### 1. 聊天与 Agent 交互

- 对话接口
- 流式响应
- 会话管理扩展点
- 聊天相关文件上传能力

### 2. 技能感知运行时

- 基于文件系统的技能包
- 按需加载技能内容
- 运行时工具注册与激活
- MCP 与自定义工具扩展能力

### 3. 知识库与 RAG

- 知识库管理
- 文档上传与解析
- 文档切片与索引
- 检索、重排与问答编排

### 4. 数据集与集成管理

- 数据集实体与绑定关系
- 数据集发布为可调用工具
- 低代码平台浏览与接入支持
- 平台配置与系统管理接口

## 配置说明

- 后端默认 profile 为 `qwen`。
- 大多数运行参数都支持通过环境变量覆盖。
- 开源版部署前请替换所有占位配置，不要直接使用示例值上线。
- 如果你要启用文件系统技能，请自行提供 `./skills` 目录，或在运行时挂载该目录。

## 文档导航

- RAG 设计资料：[`docs/rag/`](./docs/rag/)
- SSO 换 token 接口说明：[`docs/sso-exchange-token-api.md`](./docs/sso-exchange-token-api.md)
- 部署文档：[`deploy/README.md`](./deploy/README.md)、[`deploy/README.dev.md`](./deploy/README.dev.md)

## 开源版说明

面向公开发布的社区版通常会做裁剪，不会完整保留内部协作元数据、私有配置和非公开技能资源。

如果你使用的是社区版导出包，建议注意：

- 自行准备模型 API Key 与环境变量
- 如需技能能力，请自行创建或挂载 `skills` 目录
- 对外部署前请重新检查默认配置、安全边界和代理规则

## 安全提示

- 不要提交真实 API Key、密码、Token 或私有地址
- Docker 与应用配置文件默认只提供模板参考，不代表生产环境最佳实践
- 对公网开放前，请补齐认证、权限控制、密钥管理和日志审计

## 贡献方式

欢迎提交 Issue 和 Pull Request。
提交修改时，建议保持改动聚焦、描述清晰，并同步更新相关文档与配置说明。

## License

详见 [`LICENSE`](./LICENSE)。
