#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"

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

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

CURRENT_IMAGE_TAG="${IMAGE_TAG:-}"

if ! echo "$CURRENT_IMAGE_TAG" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "IMAGE_TAG in $ENV_FILE must be semantic version x.y.z, got: ${CURRENT_IMAGE_TAG:-<empty>}" >&2
  exit 1
fi

MAJOR=$(echo "$CURRENT_IMAGE_TAG" | cut -d. -f1)
MINOR=$(echo "$CURRENT_IMAGE_TAG" | cut -d. -f2)
PATCH=$(echo "$CURRENT_IMAGE_TAG" | cut -d. -f3)
NEXT_PATCH=$((PATCH + 1))
DEFAULT_VERSION="${MAJOR}.${MINOR}.${NEXT_PATCH}"

TARGET_VERSION="${RELEASE_VERSION:-}"
NO_PROMPT_RAW="${RELEASE_NO_PROMPT:-0}"
NO_PROMPT="0"

case "$NO_PROMPT_RAW" in
  1|true|TRUE|yes|YES|on|ON)
    NO_PROMPT="1"
    ;;
esac

if [ -z "$TARGET_VERSION" ]; then
  if [ -t 0 ] && [ "$NO_PROMPT" = "0" ]; then
    printf 'Current IMAGE_TAG: %s\n' "$CURRENT_IMAGE_TAG"
    printf 'Input release version (default: %s): ' "$DEFAULT_VERSION"
    read -r INPUT_VERSION
    TARGET_VERSION="${INPUT_VERSION:-$DEFAULT_VERSION}"
  else
    TARGET_VERSION="$DEFAULT_VERSION"
    if [ "$NO_PROMPT" = "1" ]; then
      printf 'RELEASE_NO_PROMPT enabled, use default release version: %s\n' "$TARGET_VERSION"
    else
      printf 'No interactive input detected, use default release version: %s\n' "$TARGET_VERSION"
    fi
  fi
fi

if ! echo "$TARGET_VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "Release version must be semantic version x.y.z, got: $TARGET_VERSION" >&2
  exit 1
fi

TMP_FILE=$(mktemp)
awk -v version="$TARGET_VERSION" '
  BEGIN { replaced = 0 }
  /^IMAGE_TAG=/ {
    print "IMAGE_TAG=" version
    replaced = 1
    next
  }
  { print }
  END {
    if (replaced == 0) {
      print "IMAGE_TAG=" version
    }
  }
' "$ENV_FILE" > "$TMP_FILE"
mv "$TMP_FILE" "$ENV_FILE"

printf 'Release version set: %s\n' "$TARGET_VERSION"
printf 'Updated file: %s\n' "$ENV_FILE"
