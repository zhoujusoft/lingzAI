#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
BUILD_SCRIPT="$SCRIPT_DIR/build.sh"
BUILD_USER="${USER:-$(id -un)}"

show_permission_issue=0

check_target_permissions() {
  show_permission_issue=0
  for module in core backend; do
    target_dir="$REPO_ROOT/$module/target"
    if [ ! -d "$target_dir" ]; then
      continue
    fi

    unwritable_path="$(find "$target_dir" ! -w -print -quit 2>/dev/null || true)"
    if [ -n "$unwritable_path" ]; then
      show_permission_issue=1
      echo "[fix-build] 检测到不可写路径: $unwritable_path" >&2
      ls -ld "$target_dir" "$unwritable_path" 2>/dev/null >&2 || true
    fi
  done
}

repair_target_permissions() {
  if ! command -v sudo >/dev/null 2>&1; then
    cat >&2 <<'EOF'
[fix-build] 未找到 sudo，无法自动修复 target 权限。
[fix-build] 请手动执行：
[fix-build]   sudo chown -R "$USER:$USER" core/target backend/target
EOF
    exit 1
  fi

  echo "[fix-build] 正在修复 target 目录权限..."
  for module in core backend; do
    target_dir="$REPO_ROOT/$module/target"
    if [ -d "$target_dir" ]; then
      sudo chown -R "$BUILD_USER:$BUILD_USER" "$target_dir"
    fi
  done
}

if [ "$#" -eq 0 ]; then
  # 默认仅构建后端，符合常见打包场景。
  set -- --backend-only
fi

check_target_permissions
if [ "$show_permission_issue" -eq 1 ]; then
  repair_target_permissions
  check_target_permissions
  if [ "$show_permission_issue" -eq 1 ]; then
    echo "[fix-build] 权限仍异常，请人工检查后重试。" >&2
    exit 1
  fi
fi

# 避免只读历史产物触发 maven-jar-plugin 覆盖失败。
rm -f "$REPO_ROOT/backend/target/backend.jar" 2>/dev/null || true

exec "$BUILD_SCRIPT" "$@"
