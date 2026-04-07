# Spring AI Skill Extension

[English Documentation](./README.md)

一个用于构建 **Skill-Aware（技能感知）AI 应用** 的多模块项目，基于 Spring AI 实现。
包含以下模块：

- `core`：可复用的技能框架
- `backend`：Spring Boot 示例后端（聊天、技能激活、文件上传）
- `frontend`：Vue 3 聊天与技能市场界面

## 项目结构

```text
.
├── core/        # 技能框架（注册、激活、懒加载、工具注入）
├── backend/     # 基于框架的 Spring Boot 应用
├── frontend/    # Vue3 + Vite 前端应用
└── pom.xml      # Maven 父工程
```

## 核心能力

- 根据激活技能动态注入工具
- 通过 `loadSkillContent(skillName)` 渐进式加载技能
- 技能生命周期控制（activate/deactivate/evict）
- 基于 SSE 的流式聊天响应
- 示例后端内置文件上传与本地文件/Python 工具

## 环境要求

- Java 17+
- Maven 3.9+
- Node.js 18+
- pnpm 8+

## 快速开始（本地）

### 1) 启动后端

在仓库根目录执行：

```bash
mvn -f backend/pom.xml spring-boot:run
```

后端默认端口：`8080`

若你已经进入 `backend/` 目录，请改用：

```bash
mvn spring-boot:run
```

说明：

- `mvn -pl backend -am ...` 只能在仓库根目录执行，因为它依赖根 `pom.xml` 中的 reactor modules。
- 若在 `backend/` 子目录执行 `mvn -pl backend -am ...`，会报 `Could not find the selected project in the reactor: backend`。

### 2) 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

前端默认端口：`5173`

Vite 已配置将 `/api` 代理到 `http://localhost:8080`。

## API 概览

基础路径：`/api`

- `POST /chat`（SSE 流式）
- `GET /skills`
- `POST /skills/{name}/activate`
- `POST /skills/{name}/deactivate`
- `POST /skills/{name}/evict`
- `POST /skills/deactivate-all`
- `POST /files/upload`（multipart）

主要控制器：`backend/src/main/java/lingzhou/agent/backend/app/ClothingSkillController.java`

## 技能加载机制

1. 技能在后端配置中完成注册。
2. 模型可调用框架工具：
   - `loadSkillContent(skillName)`
   - `loadSkillReference(skillName, referenceKey)`
   - `listActiveSkills()`
3. 技能激活后，其工具会并入模型可用工具定义。
4. 工具调用与结果通过 SSE 事件回传前端。

核心类：

- `core/src/main/java/com/semir/spring/ai/skill/core/DefaultSkillKit.java`
- `core/src/main/java/com/semir/spring/ai/skill/spi/SkillAwareAdvisor.java`
- `core/src/main/java/com/semir/spring/ai/skill/spi/SkillAwareToolCallingManager.java`

## 构建与测试

在仓库根目录：

```bash
mvn test
```

前端生产构建：

```bash
cd frontend
pnpm build
```

### 国内网络构建说明

仓库已提供依赖下载镜像默认值：

- npm/pnpm 源：`https://registry.npmmirror.com`（通过 `frontend/.npmrc`）
- Maven 镜像：通过 `MAVEN_MIRROR_URL` 配置（默认阿里云）
- pip 源：通过 `PIP_INDEX_URL` 配置（默认清华 Tuna）

容器运行时，pip 相关值会通过 `docker-compose.yml` backend 的环境变量传入。
若需切回官方源，可在 `deploy/.env`（模板：`deploy/.env.example`）中覆盖对应变量。

## Docker 说明

仓库的 Docker 部署文件统一放在 `deploy/` 下（`docker-compose.yml`、`docker-compose.dev.yml`、`docker/*.Dockerfile`）。
在生产使用前，请确认镜像构建路径、技能挂载路径与运行时配置符合你的部署环境。

### deploy 目录部署流程

```bash
cd deploy
cp .env.example .env
docker compose up -d
```

部署说明文档见：`deploy/README.md`。
也可以在仓库根目录执行 `./deploy/manage.sh up`。

### 镜像版本化构建与推送

前后端使用同一个 `deploy/release.env` 版本号：

- `REGISTRY=registry.example.com:5001
- `IMAGE_TAG=1.1.0`
- `FRONTEND_IMAGE_NAME=lingzhou-frontend`
- `BACKEND_IMAGE_NAME=lingzhou-backend`

构建后的镜像名：

- `125.75.152.167:5001/lingzhou-frontend:${IMAGE_TAG}`
- `125.75.152.167:5001/lingzhou-backend:${IMAGE_TAG}`

命令：

```bash
# 可选：先更新 IMAGE_TAG
./deploy/manage.sh release-prepare
# 使用 deploy/release.env 的版本号构建并推送前后端镜像，完成后自动打 git tag
./deploy/manage.sh release-publish
# 一条命令完成发布
./deploy/manage.sh release
```

发布逻辑：

- `release-prepare.sh` 读取并更新 `deploy/release.env` 的 `IMAGE_TAG`（默认补丁号 +1，且必须是 `x.y.z`）
- `build-and-push-images.sh` 构建并推送前后端镜像到私服
- 推送成功后自动创建本地 release 提交（`chore(release): v${IMAGE_TAG}`）
- 自动创建并推送 Git Tag：`v${IMAGE_TAG}` 到 `origin`
- 分支提交不会自动推送，需要手动执行 `git push`

## 安全说明

- 请勿提交真实 API Key 或其他密钥。
- `deploy/.env` 放部署敏感运行配置并保持 git 忽略。
- `deploy/.env.dev` 放本地开发覆盖配置并保持 git 忽略。
- `deploy/release.env` 放版本号和镜像发布配置并提交到仓库。
- 示例后端暴露了文件/Python 工具，生产部署前应增加严格权限边界。

## License

Apache License 2.0，详见 `LICENSE`。


请求地址：/api/IntegrationConnect/ExecuteApi/{apiCode}

请求头： 同之前接口一样

请求体： 
