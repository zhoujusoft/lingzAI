#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
FRONTEND_DIR="$REPO_ROOT/frontend"

backend_enabled="1"
frontend_enabled="1"
with_tests="0"
skip_frontend_install="0"

ensure_maven_targets_writable() {
  has_permission_issue="0"
  for module in core backend; do
    target_dir="$REPO_ROOT/$module/target"
    if [ ! -d "$target_dir" ]; then
      continue
    fi

    unwritable_path="$(find "$target_dir" ! -w -print -quit 2>/dev/null || true)"
    if [ -n "$unwritable_path" ]; then
      has_permission_issue="1"
      echo "[build] 错误: 检测到 $module/target 下存在不可写路径: $unwritable_path" >&2
      ls -ld "$target_dir" "$unwritable_path" 2>/dev/null >&2 || true
    fi
  done

  if [ "$has_permission_issue" = "1" ]; then
    cat >&2 <<'EOF'
[build] 请先修复 target 目录权限后再重试。
[build] 例如：
[build]   sudo chown -R "$USER:$USER" core/target backend/target
[build] 或手动清理后重新构建。
EOF
    exit 1
  fi
}

usage() {
  cat <<'EOF'
Usage:
  ./deploy/manage.sh build [options]

Options:
  --backend-only           仅构建后端 backend.jar
  --frontend-only          仅构建前端 dist
  --with-tests             后端构建时不跳过测试
  --skip-frontend-install  跳过前端 pnpm install
  -h, --help               查看帮助
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --backend-only)
      frontend_enabled="0"
      ;;
    --frontend-only)
      backend_enabled="0"
      ;;
    --with-tests)
      with_tests="1"
      ;;
    --skip-frontend-install)
      skip_frontend_install="1"
      ;;
    -h|--help|help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

if [ "$backend_enabled" = "0" ] && [ "$frontend_enabled" = "0" ]; then
  echo "Invalid options: --backend-only and --frontend-only cannot be used together." >&2
  exit 1
fi

if [ "$backend_enabled" = "1" ]; then
  if ! command -v mvn >/dev/null 2>&1; then
    echo "mvn command not found. Please install Maven first." >&2
    exit 1
  fi

  ensure_maven_targets_writable

  echo "[build] 构建后端..."
  if [ "$with_tests" = "1" ]; then
    mvn -f "$REPO_ROOT/pom.xml" -pl backend -am package
  else
    mvn -f "$REPO_ROOT/pom.xml" -pl backend -am -DskipTests package
  fi

  echo "[build] 后端产物: backend/target/backend.jar"
fi

if [ "$frontend_enabled" = "1" ]; then
  if ! command -v pnpm >/dev/null 2>&1; then
    echo "pnpm command not found. Please install pnpm first." >&2
    exit 1
  fi

  echo "[build] 构建前端..."
  cd "$FRONTEND_DIR"
  if [ "$skip_frontend_install" != "1" ]; then
    pnpm install
  fi
  pnpm build

  echo "[build] 前端产物: frontend/dist/"
fi

echo "[build] 完成。"
