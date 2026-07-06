# Flyway Migration Conventions

- 目录：`backend/src/main/resources/db/migration/`
- 命名：`V<version>__<description>.sql`
- 版本建议：与平台版本对齐，例如 `V1_1_0__init.sql`

## 基线规则

- `deploy/lingz/db/schema.sql` 是部署新库初始化的结构基线。
- 后端 jar 不携带 `schema.sql`，启动时不再从 jar 内 schema 推导 baseline。
- `deploy/lingz/db/schema.sql` 必须初始化 `flyway_schema_history` 并写入与快照内容一致的 baseline 行。
- `db/migration` 只承载 schema 快照 baseline 之后的增量变更。
- 存量非空库如果没有 `flyway_schema_history`，必须先人工确认真实 schema 版本并补写正确 baseline；禁止自动 baseline 到当前最新版本。
- 存量库如果已经有 `flyway_schema_history`，应用启动时只执行 Flyway migrate；历史漂移需要用明确的一次性 migration 或人工 SQL 修复。
- `IMAGE_TAG`、`APP_VERSION`、Docker 镜像 tag、环境变量都不是 Flyway baseline 来源。

## 不可变规则

- 已发布的 migration **禁止** 删除、改名、改版本号、改脚本内容。
- 新增变更只能追加新版本，不能把旧版本复用于新的 schema/data 变更。
- `deploy/lingz/db/schema.sql` 可以继续前推 baseline-version，但历史 migration 文件必须继续保留在仓库中。

## 并行维护规则

为兼容现有升级链路，新增数据库变更时需同时更新：

1. `db/migration`（Flyway 启动自动迁移）
2. `db/changes`（现有 dev 运维脚本链路）
3. `deploy/lingz/db/schema.sql`（新库初始化基线）

## schema baseline 维护规则

- `deploy/lingz/db/schema.sql` 文件头的 `-- flyway-baseline-version` 必须等于该 schema 快照真实包含的版本。
- `deploy/lingz/db/schema.sql` 末尾写入 `flyway_schema_history.version` 的值必须与文件头 baseline 一致。
- 前推 baseline 时，只能在 schema 已包含该版本全部结构与基础种子数据后修改 baseline。
- Flyway 基线由维护 `deploy/lingz/db/schema.sql` 的数据库变更提交者负责；应用启动配置不负责自动推导或自动写入 baseline。
- 后端 jar 不携带 `db/schema.sql`，不要在 `backend/src/main/resources/db/` 下新增 schema 快照。
- `SPRING_FLYWAY_BASELINE_VERSION` 不再作为部署配置使用。

## 幂等要求

- 新表：使用 `CREATE TABLE IF NOT EXISTS`。
- 新字段：先查 `information_schema.COLUMNS`，再动态执行 `ALTER TABLE`。
- 新索引：先查 `information_schema.STATISTICS`，再动态执行 `ALTER TABLE ... ADD KEY`。
- 种子数据：使用 `INSERT ... WHERE NOT EXISTS` 或稳定唯一键上的 `ON DUPLICATE KEY UPDATE`。
