#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
DEPLOY_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"

echo "[dev-up] 执行 dev 数据库升级..."
"$SCRIPT_DIR/dev-db-update.sh"

echo "[dev-up] 启动 dev 中间件..."
cd "$DEPLOY_DIR"
docker compose --env-file .env.dev -f docker-compose.dev.yml up -d "$@"

cat <<'EOF'

[dev-up] Dev 中间件已启动。

后续请分别在本机启动应用进程：

1. 后端
   mvn -pl backend -am org.springframework.boot:spring-boot-maven-plugin:run

2. 前端
   cd frontend
   pnpm dev
EOF
