#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
DEPLOY_DIR="$REPO_ROOT/deploy"

ENV_FILE_ARG="${1:-deploy/release.env}"
case "$ENV_FILE_ARG" in
  /*) ENV_FILE="$ENV_FILE_ARG" ;;
  *) ENV_FILE="$REPO_ROOT/$ENV_FILE_ARG" ;;
esac

if [ ! -f "$ENV_FILE" ]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi
ENV_FILE="$(cd "$(dirname "$ENV_FILE")" && pwd)/$(basename "$ENV_FILE")"
case "$ENV_FILE" in
  "$REPO_ROOT"/*) ENV_FILE_GIT_PATH="${ENV_FILE#"$REPO_ROOT"/}" ;;
  *) ENV_FILE_GIT_PATH="$ENV_FILE_ARG" ;;
esac

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

is_true() {
  case "${1:-}" in
    1|true|TRUE|yes|YES|on|ON) return 0 ;;
    *) return 1 ;;
  esac
}

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

if [ -f "$DEPLOY_DIR/.env" ]; then
  ENV_DOT_FILE="$DEPLOY_DIR/.env"
  load_env_if_unset "REGISTRY_USERNAME" "$ENV_DOT_FILE"
  load_env_if_unset "REGISTRY_PASSWORD" "$ENV_DOT_FILE"
  load_env_if_unset "PNPM_REGISTRY" "$ENV_DOT_FILE"
  load_env_if_unset "VITE_BASE_PATH" "$ENV_DOT_FILE"
  load_env_if_unset "VITE_BASE_URL" "$ENV_DOT_FILE"
  load_env_if_unset "MAVEN_MIRROR_URL" "$ENV_DOT_FILE"
fi

REGISTRY="${REGISTRY:-registry.example.com:5001}"
IMAGE_TAG="${IMAGE_TAG:-}"
FRONTEND_IMAGE_NAME="${FRONTEND_IMAGE_NAME:-lingzhou-frontend}"
BACKEND_IMAGE_NAME="${BACKEND_IMAGE_NAME:-lingzhou-backend}"
FORCE_CLASSIC="${FORCE_CLASSIC:-0}"
FORCE_BUILDX="${FORCE_BUILDX:-0}"

if [ -z "$IMAGE_TAG" ]; then
  echo "IMAGE_TAG is required in $ENV_FILE (example: IMAGE_TAG=1.1.0)" >&2
  exit 1
fi

if ! echo "$IMAGE_TAG" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "IMAGE_TAG must be semantic version x.y.z, got: $IMAGE_TAG" >&2
  exit 1
fi

ALLOW_DIRTY="0"
if [ -n "${RELEASE_ALLOW_DIRTY:-}" ] && is_true "${RELEASE_ALLOW_DIRTY:-}"; then
  ALLOW_DIRTY="1"
  echo "Warning: RELEASE_ALLOW_DIRTY enabled. Skipping clean-worktree enforcement."
fi

FRONTEND_IMAGE="${REGISTRY}/${FRONTEND_IMAGE_NAME}:${IMAGE_TAG}"
BACKEND_IMAGE="${REGISTRY}/${BACKEND_IMAGE_NAME}:${IMAGE_TAG}"
FRONTEND_CACHE_REF="${FRONTEND_CACHE_REF:-${REGISTRY}/${FRONTEND_IMAGE_NAME}:buildcache}"
BACKEND_CACHE_REF="${BACKEND_CACHE_REF:-${REGISTRY}/${BACKEND_IMAGE_NAME}:buildcache}"
TAG="v${IMAGE_TAG}"

if is_true "$FORCE_CLASSIC" && is_true "$FORCE_BUILDX"; then
  echo "Invalid options: FORCE_CLASSIC and FORCE_BUILDX cannot both be enabled." >&2
  exit 1
fi

BUILDER_MODE="classic"
BUILDX_READY="0"
if ! is_true "$FORCE_CLASSIC"; then
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
fi

if is_true "$FORCE_BUILDX" && [ "$BUILDX_READY" != "1" ]; then
  echo "FORCE_BUILDX is enabled, but buildx is unavailable or not ready." >&2
  exit 1
fi
if [ "$BUILDX_READY" = "1" ]; then
  BUILDER_MODE="buildx"
fi

echo "Release preflight:"
echo "  Repo root: $REPO_ROOT"
echo "  Env file: $ENV_FILE_GIT_PATH"
echo "  Frontend image: $FRONTEND_IMAGE"
echo "  Backend image: $BACKEND_IMAGE"
echo "  Builder mode: $BUILDER_MODE"
if [ "$BUILDER_MODE" = "buildx" ]; then
  echo "  Frontend cache ref: $FRONTEND_CACHE_REF"
  echo "  Backend cache ref: $BACKEND_CACHE_REF"
fi
echo "  Git tag: $TAG"
if [ "$ALLOW_DIRTY" = "1" ]; then
  echo "  Dirty worktree allowed: yes (RELEASE_ALLOW_DIRTY)"
else
  echo "  Dirty worktree allowed: no"
fi

if git -C "$REPO_ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1 && [ "$ALLOW_DIRTY" = "0" ]; then
  STATUS_LINES="$(git -C "$REPO_ROOT" status --porcelain)"
  if [ -n "$STATUS_LINES" ]; then
    DISALLOWED_CHANGES="$(printf '%s\n' "$STATUS_LINES" | awk '
      {
        path = substr($0, 4)
        # git porcelain may contain rename form: "old/path -> new/path"
        n = split(path, parts, / -> /)
        ignore = 1
        for (i = 1; i <= n; i++) {
          if (parts[i] !~ /^\.trellis\//) {
            ignore = 0
            break
          }
        }
        if (path != env_path && !ignore) {
          print $0
        }
      }
    ' env_path="$ENV_FILE_GIT_PATH")"
    if [ -n "$DISALLOWED_CHANGES" ]; then
      echo "Release blocked: worktree must be clean (only $ENV_FILE_GIT_PATH and .trellis/* changes are allowed)." >&2
      echo "Detected disallowed changes:" >&2
      printf '%s\n' "$DISALLOWED_CHANGES" >&2
      echo "How to continue:" >&2
      echo "  1) Commit/stash these changes, then rerun this script" >&2
      echo "  2) (Not recommended) bypass with 'RELEASE_ALLOW_DIRTY=1 ./deploy/scripts/build-and-push-images.sh deploy/release.env'" >&2
      exit 1
    fi
  fi
fi

if [ -n "${REGISTRY_USERNAME:-}" ] && [ -n "${REGISTRY_PASSWORD:-}" ]; then
  printf '%s' "$REGISTRY_PASSWORD" | docker login "$REGISTRY" --username "$REGISTRY_USERNAME" --password-stdin
fi

if [ "$BUILDER_MODE" = "buildx" ]; then
  (
    cd "$REPO_ROOT"
    if [ -n "${BUILDX_BUILDER:-}" ]; then
      docker buildx build \
        --builder "$BUILDX_BUILDER" \
        -f deploy/docker/frontend.Dockerfile \
        -t "$FRONTEND_IMAGE" \
        --build-arg "PNPM_REGISTRY=${PNPM_REGISTRY:-https://registry.npmmirror.com}" \
        --build-arg "VITE_BASE_PATH=${VITE_BASE_PATH:-/}" \
        --build-arg "VITE_BASE_URL=${VITE_BASE_URL:-}" \
        --cache-from "type=registry,ref=${FRONTEND_CACHE_REF}" \
        --cache-to "type=registry,ref=${FRONTEND_CACHE_REF},mode=max" \
        --provenance=false \
        --sbom=false \
        --push \
        frontend
    else
      docker buildx build \
        -f deploy/docker/frontend.Dockerfile \
        -t "$FRONTEND_IMAGE" \
        --build-arg "PNPM_REGISTRY=${PNPM_REGISTRY:-https://registry.npmmirror.com}" \
        --build-arg "VITE_BASE_PATH=${VITE_BASE_PATH:-/}" \
        --build-arg "VITE_BASE_URL=${VITE_BASE_URL:-}" \
        --cache-from "type=registry,ref=${FRONTEND_CACHE_REF}" \
        --cache-to "type=registry,ref=${FRONTEND_CACHE_REF},mode=max" \
        --provenance=false \
        --sbom=false \
        --push \
        frontend
    fi
  )

  (
    cd "$REPO_ROOT"
    if [ -n "${BUILDX_BUILDER:-}" ]; then
      docker buildx build \
        --builder "$BUILDX_BUILDER" \
        -f deploy/docker/backend.Dockerfile \
        -t "$BACKEND_IMAGE" \
        --build-arg "MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL:-https://maven.aliyun.com/repository/public}" \
        --cache-from "type=registry,ref=${BACKEND_CACHE_REF}" \
        --cache-to "type=registry,ref=${BACKEND_CACHE_REF},mode=max" \
        --provenance=false \
        --sbom=false \
        --push \
        .
    else
      docker buildx build \
        -f deploy/docker/backend.Dockerfile \
        -t "$BACKEND_IMAGE" \
        --build-arg "MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL:-https://maven.aliyun.com/repository/public}" \
        --cache-from "type=registry,ref=${BACKEND_CACHE_REF}" \
        --cache-to "type=registry,ref=${BACKEND_CACHE_REF},mode=max" \
        --provenance=false \
        --sbom=false \
        --push \
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

  (
    cd "$REPO_ROOT"
    docker build \
      -f deploy/docker/backend.Dockerfile \
      -t "$BACKEND_IMAGE" \
      --build-arg "MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL:-https://maven.aliyun.com/repository/public}" \
      .
  )

  docker push "$FRONTEND_IMAGE"
  docker push "$BACKEND_IMAGE"
fi

echo "Pushed:"
echo "  $FRONTEND_IMAGE"
echo "  $BACKEND_IMAGE"

if git -C "$REPO_ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  if git -C "$REPO_ROOT" diff --quiet -- "$ENV_FILE_GIT_PATH" \
    && git -C "$REPO_ROOT" diff --cached --quiet -- "$ENV_FILE_GIT_PATH"; then
    echo "$ENV_FILE_GIT_PATH has no version change; skip release commit." >&2
  else
    git -C "$REPO_ROOT" add "$ENV_FILE_GIT_PATH"
    git -C "$REPO_ROOT" commit -m "chore(release): v${IMAGE_TAG}"
    echo "Created release commit: chore(release): v${IMAGE_TAG}"
  fi

  FORCE_TAG_OVERWRITE="${RELEASE_FORCE_TAG_OVERWRITE:-0}"
  LOCAL_TAG_EXISTS="0"
  REMOTE_TAG_EXISTS="0"

  if git -C "$REPO_ROOT" rev-parse "$TAG" >/dev/null 2>&1; then
    LOCAL_TAG_EXISTS="1"
  fi

  if git -C "$REPO_ROOT" remote get-url origin >/dev/null 2>&1; then
    if git -C "$REPO_ROOT" ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1; then
      REMOTE_TAG_EXISTS="1"
    fi
  fi

  if [ "$LOCAL_TAG_EXISTS" = "1" ] || [ "$REMOTE_TAG_EXISTS" = "1" ]; then
    if is_true "$FORCE_TAG_OVERWRITE"; then
      echo "Warning: RELEASE_FORCE_TAG_OVERWRITE enabled. Existing tag will be replaced: $TAG"
      if [ "$LOCAL_TAG_EXISTS" = "1" ]; then
        git -C "$REPO_ROOT" tag -d "$TAG"
      fi
      if [ "$REMOTE_TAG_EXISTS" = "1" ] && git -C "$REPO_ROOT" remote get-url origin >/dev/null 2>&1; then
        git -C "$REPO_ROOT" push origin ":refs/tags/$TAG"
      fi
    else
      echo "Release blocked: git tag already exists: $TAG" >&2
      if [ "$LOCAL_TAG_EXISTS" = "1" ]; then
        echo "  - local tag exists" >&2
      fi
      if [ "$REMOTE_TAG_EXISTS" = "1" ]; then
        echo "  - origin tag exists" >&2
      fi
      echo "How to continue:" >&2
      echo "  1) Use a new version (recommended)" >&2
      echo "  2) Overwrite tag explicitly: RELEASE_FORCE_TAG_OVERWRITE=1 ./deploy/scripts/build-and-push-images.sh deploy/release.env" >&2
      exit 1
    fi
  fi

  git -C "$REPO_ROOT" tag -a "$TAG" -m "release ${IMAGE_TAG}"
  echo "Created git tag: $TAG"

  if git -C "$REPO_ROOT" remote get-url origin >/dev/null 2>&1; then
    git -C "$REPO_ROOT" push origin "$TAG"
    echo "Pushed git tag to origin: $TAG"
  else
    echo "No origin remote found, skip git tag push"
  fi

  echo "Branch commit is local only. Run 'git push' manually when ready."
fi
