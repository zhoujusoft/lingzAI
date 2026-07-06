#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"

usage() {
  cat <<'EOF'
Usage:
  ./deploy/manage.sh release [options] [deploy/release.env]

Options:
  --platform     指定目标平台，例如 linux/arm64；非 amd64 平台会自动追加镜像 tag 后缀
  -h, --help     查看帮助
EOF
}

ENV_FILE_ARG="deploy/release.env"
ENV_FILE_SPECIFIED="0"
TARGET_PLATFORM_ARG=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --platform)
      shift
      if [ "$#" -eq 0 ]; then
        echo "Missing value for --platform" >&2
        usage
        exit 1
      fi
      TARGET_PLATFORM_ARG="$1"
      ;;
    -h|--help|help)
      usage
      exit 0
      ;;
    -*)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
    *)
      if [ "$ENV_FILE_SPECIFIED" = "1" ]; then
        echo "Only one env file path is allowed." >&2
        usage
        exit 1
      fi
      ENV_FILE_ARG="$1"
      ENV_FILE_SPECIFIED="1"
      ;;
  esac
  shift
done

case "$ENV_FILE_ARG" in
  /*) ENV_FILE="$ENV_FILE_ARG" ;;
  *) ENV_FILE="$REPO_ROOT/$ENV_FILE_ARG" ;;
esac

if [ ! -f "$ENV_FILE" ]; then
  echo "Release env file not found: $ENV_FILE_ARG" >&2
  echo "Example: ./deploy/manage.sh release deploy/release.env" >&2
  exit 1
fi

"$SCRIPT_DIR/release-prepare.sh" "$ENV_FILE_ARG"
if [ -n "$TARGET_PLATFORM_ARG" ]; then
  "$SCRIPT_DIR/build-and-push-images.sh" --platform "$TARGET_PLATFORM_ARG" "$ENV_FILE_ARG"
else
  "$SCRIPT_DIR/build-and-push-images.sh" "$ENV_FILE_ARG"
fi
