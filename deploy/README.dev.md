# 本地开发中间件启动指南

本文档仅用于**本地开发环境的中间件启动**。
与生产/部署文档 `deploy/README.md` 不同。

## 适用范围

基于 `deploy/docker-compose.dev.yml` 启动以下中间件：

- MySQL（`3306`）
- Redis（`16379`）
- MinIO（`19000`、`19001`）
- Elasticsearch（`9200`、`9300`）

本文档**不包含**后端/前端应用进程的启动。

## 前置条件

- 已安装 Docker 与 Docker Compose
- 本机端口未被占用（见上方端口）

## 快速启动

在仓库根目录执行：

```bash
cp deploy/.env.dev.example deploy/.env.dev
./deploy/manage.sh dev-up
```

说明：

- `dev-up` 是推荐入口，会先执行数据库增量升级，再启动 `docker-compose.dev.yml` 中的中间件服务。
- `dev-up` 只负责 dev 中间件与数据库准备，不会代替你启动本机的后端、前端进程。
- `middleware-db-update` 仅用于本地 dev，中间件中的 MySQL 会先被拉起，然后按 `backend/src/main/resources/db/changes/*.sql` 顺序执行增量 SQL。
- 脚本会在数据库中维护一张 `dev_schema_migrations` 表，已经执行过的 SQL 文件不会重复执行。
- 若你清空了 `deploy/data/dev/mysql`，MySQL 会先按 `backend/src/main/resources/db/schema.sql` 初始化，再执行上述增量 SQL。

## 查看日志

```bash
./deploy/manage.sh middleware-logs
```

若只想单独执行数据库升级：

```bash
./deploy/manage.sh middleware-db-update
```

若只想单独启动中间件、不执行数据库升级：

```bash
./deploy/manage.sh middleware-up
```

## 停止服务

```bash
./deploy/manage.sh middleware-down
```

## 检查服务状态

```bash
docker ps
docker compose --env-file deploy/.env.dev -f deploy/docker-compose.dev.yml ps
```

## （可选）启动后端/前端

在 `./deploy/manage.sh dev-up` 执行完成后：

```bash
# 启动后端（在仓库根目录执行，推荐）
mvn -f backend/pom.xml spring-boot:run

# 新开终端启动前端
cd frontend
pnpm dev
```

若你已进入 `backend/` 目录，请使用：

```bash
mvn spring-boot:run
```

说明：

- 仓库根目录推荐命令应直接指定 `backend/pom.xml`，避免 Maven 在父工程 `lingzhou-agent` 上执行 `spring-boot:run`。
- 若在 `backend/` 目录内执行，`mvn spring-boot:run` 会使用 `backend/pom.xml` 中已配置的主类 `lingzhou.agent.backend.SpringApplication`。
- 不要在 `backend/` 目录内执行 `mvn -pl backend -am ...`；该参数需要根 `pom.xml` 的 reactor，放在子模块目录会报 `Could not find the selected project in the reactor: backend`。

后端启动前请确认 JDK 版本为 17（或更高）：

```bash
java -version
mvn -v
```

## 常见问题

- 若端口冲突，请修改 `deploy/docker-compose.dev.yml` 中的端口映射。
- 若环境变量缺失，请对照 `deploy/.env.dev.example` 检查 `deploy/.env.dev`。
- 若 Elasticsearch 健康检查较慢，请稍候并通过 `middleware-logs` 查看日志。
- 若出现 `Public Key Retrieval is not allowed`，请检查后端数据源 URL 是否包含 `allowPublicKeyRetrieval=true`。
- 若出现 `Could not find the selected project in the reactor: backend`：
  - 请先确认当前目录。
  - 在仓库根目录执行：`mvn -f backend/pom.xml spring-boot:run`
  - 在 `backend/` 目录执行：`mvn spring-boot:run`
