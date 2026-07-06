#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
DEPLOY_DIR="$REPO_ROOT/deploy"
. "$SCRIPT_DIR/lib/release-meta.sh"

usage() {
  cat <<'EOF'
Usage:
  ./deploy/manage.sh build-images [options] [deploy/release.env]

Options:
  --push         构建完成后推送镜像（不包含登录逻辑）
  --platform     指定目标平台，例如 linux/arm64；非 amd64 平台会自动追加镜像 tag 后缀
  --auto-bump    构建前自动升级 IMAGE_TAG 的 patch 版本
  -h, --help     查看帮助
EOF
}

PUSH_IMAGES="0"
AUTO_BUMP="0"
ENV_FILE_ARG="deploy/release.env"
ENV_FILE_SPECIFIED="0"
TARGET_PLATFORM_CLI=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --push)
      PUSH_IMAGES="1"
      ;;
    --platform)
      shift
      if [ "$#" -eq 0 ]; then
        echo "Missing value for --platform" >&2
        usage
        exit 1
      fi
      TARGET_PLATFORM_CLI="$1"
      ;;
    --auto-bump)
      AUTO_BUMP="1"
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
  echo "Env file not found: $ENV_FILE" >&2
  usage >&2
  exit 1
fi
ENV_FILE="$(cd "$(dirname "$ENV_FILE")" && pwd)/$(basename "$ENV_FILE")"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker command not found. Please install Docker first." >&2
  exit 1
fi

if [ "$AUTO_BUMP" = "1" ]; then
  if ! docker version >/dev/null 2>&1; then
    echo "[build-images] Docker daemon 不可访问，已取消自动升级版本号（避免失败重试导致版本重复递增）。" >&2
    echo "[build-images] 请先确认 docker 可用后重试，或去掉 --auto-bump 手工指定 IMAGE_TAG。" >&2
    exit 1
  fi
  echo "[build-images] 自动升级版本号（IMAGE_TAG）..."
  RELEASE_NO_PROMPT=1 "$SCRIPT_DIR/release-prepare.sh" "$ENV_FILE"
fi

read_dotenv_value() {
  key="$1"
  file="$2"
  if [ ! -f "$file" ]; then
    return 0
  fi

  line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}=" "$file" | tail -n 1 || true)"
  if [ -z "$line" ]; then
    return 0
  fi

  value="${line#*=}"
  value="$(printf '%s' "$value" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  case "$value" in
    \"*\")
      value="${value#\"}"
      value="${value%\"}"
      ;;
    \'*\')
      value="${value#\'}"
      value="${value%\'}"
      ;;
  esac

  printf '%s' "$value"
}

load_env_if_unset() {
  key="$1"
  file="$2"
  eval "current_value=\${$key:-}"
  if [ -n "$current_value" ]; then
    return 0
  fi
  loaded_value="$(read_dotenv_value "$key" "$file")"
  if [ -n "$loaded_value" ]; then
    export "$key=$loaded_value"
  fi
}

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

if [ -f "$DEPLOY_DIR/.env" ]; then
  ENV_DOT_FILE="$DEPLOY_DIR/.env"
  load_env_if_unset "PNPM_REGISTRY" "$ENV_DOT_FILE"
  load_env_if_unset "VITE_BASE_PATH" "$ENV_DOT_FILE"
  load_env_if_unset "VITE_BASE_URL" "$ENV_DOT_FILE"
fi

REGISTRY="${REGISTRY:-125.75.152.167:5001}"
IMAGE_TAG="${IMAGE_TAG:-}"
FRONTEND_IMAGE_NAME="${FRONTEND_IMAGE_NAME:-lingzhou-frontend}"
BACKEND_IMAGE_NAME="${BACKEND_IMAGE_NAME:-lingzhou-backend}"
TARGET_PLATFORM_RAW="${TARGET_PLATFORM_CLI:-${TARGET_PLATFORM:-}}"

if [ -z "$IMAGE_TAG" ]; then
  echo "IMAGE_TAG is required in $ENV_FILE (example: IMAGE_TAG=1.4.2)" >&2
  exit 1
fi

if ! validate_image_tag "$IMAGE_TAG"; then
  echo "IMAGE_TAG must be base semantic version x.y.z, got: $IMAGE_TAG" >&2
  exit 1
fi

TARGET_PLATFORM="$(normalize_target_platform "$TARGET_PLATFORM_RAW")"
warn_if_non_default_platform_generates_variant_tag "$TARGET_PLATFORM"
EFFECTIVE_IMAGE_TAG="$(effective_image_tag "$IMAGE_TAG" "$TARGET_PLATFORM")"

FRONTEND_IMAGE="${REGISTRY}/${FRONTEND_IMAGE_NAME}:${EFFECTIVE_IMAGE_TAG}"
BACKEND_IMAGE="${REGISTRY}/${BACKEND_IMAGE_NAME}:${EFFECTIVE_IMAGE_TAG}"

BUILDX_READY="0"
if docker buildx version >/dev/null 2>&1; then
  if [ -n "${BUILDX_BUILDER:-}" ]; then
    if docker buildx inspect "$BUILDX_BUILDER" >/dev/null 2>&1 \
      && docker buildx inspect --bootstrap "$BUILDX_BUILDER" >/dev/null 2>&1; then
      BUILDX_READY="1"
    fi
  else
    if docker buildx inspect >/dev/null 2>&1 \
      && docker buildx inspect --bootstrap >/dev/null 2>&1; then
      BUILDX_READY="1"
    fi
  fi
fi

if [ "$BUILDX_READY" != "1" ] && [ "$TARGET_PLATFORM" != "linux/amd64" ]; then
  echo "[build-images] 当前镜像目标平台为 $TARGET_PLATFORM，但本机 buildx 不可用。" >&2
  echo "[build-images] 请先启用 docker buildx，或改用默认 amd64 目标。" >&2
  exit 1
fi

echo "[build-images] Repo root: $REPO_ROOT"
echo "[build-images] Env file: $ENV_FILE"
echo "[build-images] Target platform: $TARGET_PLATFORM"
echo "[build-images] Base image tag: $IMAGE_TAG"
echo "[build-images] Effective image tag: $EFFECTIVE_IMAGE_TAG"
echo "[build-images] Frontend image: $FRONTEND_IMAGE"
echo "[build-images] Backend image: $BACKEND_IMAGE"

PIP_PLATFORM="$(pip_platform_for_target "$TARGET_PLATFORM")"
echo "[build-images] PIP platform: $PIP_PLATFORM"

echo "[build-images] 先构建后端 jar..."
"$SCRIPT_DIR/build.sh" --backend-only

echo "[build-images] 构建前端镜像..."
if [ "$BUILDX_READY" = "1" ]; then
  BUILDX_OUTPUT_FLAG="--load"
  if [ "$PUSH_IMAGES" = "1" ]; then
    BUILDX_OUTPUT_FLAG="--push"
  fi

  (
    cd "$REPO_ROOT"
    if [ -n "${BUILDX_BUILDER:-}" ]; then
      docker buildx build \
        --builder "$BUILDX_BUILDER" \
        --platform "$TARGET_PLATFORM" \
        -f deploy/docker/frontend.Dockerfile \
        -t "$FRONTEND_IMAGE" \
        --build-arg "PNPM_REGISTRY=${PNPM_REGISTRY:-https://registry.npmmirror.com}" \
        --build-arg "VITE_BASE_PATH=${VITE_BASE_PATH:-/}" \
        --build-arg "VITE_BASE_URL=${VITE_BASE_URL:-}" \
        --provenance=false \
        --sbom=false \
        "$BUILDX_OUTPUT_FLAG" \
        frontend
    else
      docker buildx build \
        --platform "$TARGET_PLATFORM" \
        -f deploy/docker/frontend.Dockerfile \
        -t "$FRONTEND_IMAGE" \
        --build-arg "PNPM_REGISTRY=${PNPM_REGISTRY:-https://registry.npmmirror.com}" \
        --build-arg "VITE_BASE_PATH=${VITE_BASE_PATH:-/}" \
        --build-arg "VITE_BASE_URL=${VITE_BASE_URL:-}" \
        --provenance=false \
        --sbom=false \
        "$BUILDX_OUTPUT_FLAG" \
        frontend
    fi
  )

  echo "[build-images] 构建后端镜像..."
  (
    cd "$REPO_ROOT"
    if [ -n "${BUILDX_BUILDER:-}" ]; then
      docker buildx build \
        --builder "$BUILDX_BUILDER" \
        --platform "$TARGET_PLATFORM" \
        -f deploy/docker/backend.Dockerfile \
        -t "$BACKEND_IMAGE" \
        --build-arg "PIP_PLATFORM=${PIP_PLATFORM}" \
        --provenance=false \
        --sbom=false \
        "$BUILDX_OUTPUT_FLAG" \
        .
    else
      docker buildx build \
        --platform "$TARGET_PLATFORM" \
        -f deploy/docker/backend.Dockerfile \
        -t "$BACKEND_IMAGE" \
        --build-arg "PIP_PLATFORM=${PIP_PLATFORM}" \
        --provenance=false \
        --sbom=false \
        "$BUILDX_OUTPUT_FLAG" \
        .
    fi
  )
else
  (
    cd "$REPO_ROOT"
    docker build \
      -f deploy/docker/frontend.Dockerfile \
      -t "$FRONTEND_IMAGE" \
      --build-arg "PNPM_REGISTRY=${PNPM_REGISTRY:-https://registry.npmmirror.com}" \
      --build-arg "VITE_BASE_PATH=${VITE_BASE_PATH:-/}" \
      --build-arg "VITE_BASE_URL=${VITE_BASE_URL:-}" \
      frontend
  )

  echo "[build-images] 构建后端镜像..."
  (
    cd "$REPO_ROOT"
    docker build \
      -f deploy/docker/backend.Dockerfile \
      -t "$BACKEND_IMAGE" \
      --build-arg "PIP_PLATFORM=${PIP_PLATFORM}" \
      .
  )

  if [ "$PUSH_IMAGES" = "1" ]; then
    echo "[build-images] 推送前端镜像..."
    docker push "$FRONTEND_IMAGE"

    echo "[build-images] 推送后端镜像..."
    docker push "$BACKEND_IMAGE"
  fi
fi

if [ "$PUSH_IMAGES" = "1" ]; then
  echo "[build-images] 完成（已构建并推送）:"
else
  echo "[build-images] 完成（仅本地构建，未推送镜像）:"
fi
echo "  $FRONTEND_IMAGE"
echo "  $BACKEND_IMAGE"
