#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
DEPLOY_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$DEPLOY_DIR/.env.dev"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.dev.yml"
MYSQL_SERVICE="mysql-db"
MIGRATIONS_DIR="$DEPLOY_DIR/../backend/src/main/resources/db/changes"
MIGRATION_TABLE="dev_schema_migrations"

if [ ! -f "$ENV_FILE" ]; then
  echo "缺少 $ENV_FILE，请先准备 dev 环境变量文件。" >&2
  exit 1
fi

if [ ! -d "$MIGRATIONS_DIR" ]; then
  echo "未找到增量 SQL 目录：$MIGRATIONS_DIR" >&2
  exit 1
fi

cd "$DEPLOY_DIR"

read_env_value() {
  key="$1"
  value="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 | cut -d '=' -f 2- || true)"
  printf '%s' "$value"
}

MYSQL_ROOT_PASSWORD="change-me-password"
MYSQL_DATABASE="$(read_env_value MYSQL_DATABASE)"

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD 未配置}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE 未配置}"

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

mysql_exec() {
  compose exec -T "$MYSQL_SERVICE" mysql -uroot "-p$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"
}

mysql_query() {
  compose exec -T "$MYSQL_SERVICE" mysql -N -s -uroot "-p$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "$1"
}

wait_for_mysql() {
  attempts=0
  max_attempts=30

  while [ "$attempts" -lt "$max_attempts" ]; do
    if compose exec -T "$MYSQL_SERVICE" mysqladmin ping -h 127.0.0.1 -uroot "-p$MYSQL_ROOT_PASSWORD" --silent >/dev/null 2>&1; then
      return 0
    fi
    attempts=$((attempts + 1))
    sleep 2
  done

  echo "等待 dev MySQL 就绪超时，请检查容器日志。" >&2
  exit 1
}

ensure_migration_table() {
  mysql_query "CREATE TABLE IF NOT EXISTS \`$MIGRATION_TABLE\` (
    \`filename\` varchar(255) NOT NULL COMMENT '已执行的增量 SQL 文件名',
    \`executed_at\` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    PRIMARY KEY (\`filename\`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Dev 环境数据库增量执行记录';" >/dev/null
}

is_applied() {
  filename="$1"
  result="$(mysql_query "SELECT filename FROM \`$MIGRATION_TABLE\` WHERE filename = '${filename}' LIMIT 1;")"
  [ "$result" = "$filename" ]
}

mark_applied() {
  filename="$1"
  mysql_query "INSERT INTO \`$MIGRATION_TABLE\` (filename) VALUES ('${filename}');" >/dev/null
}

echo "[dev-db-update] 启动 MySQL 容器..."
compose up -d "$MYSQL_SERVICE" >/dev/null

echo "[dev-db-update] 等待 MySQL 就绪..."
wait_for_mysql

ensure_migration_table

applied_count=0
skipped_count=0

for sql_file in "$MIGRATIONS_DIR"/*.sql; do
  if [ ! -f "$sql_file" ]; then
    continue
  fi

  filename="$(basename "$sql_file")"
  if is_applied "$filename"; then
    echo "[dev-db-update] 已跳过: $filename"
    skipped_count=$((skipped_count + 1))
    continue
  fi

  echo "[dev-db-update] 执行: $filename"
  mysql_exec < "$sql_file"
  mark_applied "$filename"
  applied_count=$((applied_count + 1))
done

echo "[dev-db-update] 完成，新增执行 $applied_count 个 SQL，跳过 $skipped_count 个已执行 SQL。"
