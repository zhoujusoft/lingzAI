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
  echo "Release env file not found: $ENV_FILE_ARG" >&2
  echo "Example: ./deploy/manage.sh release deploy/release.env" >&2
  exit 1
fi

"$SCRIPT_DIR/release-prepare.sh" "$ENV_FILE_ARG"
"$SCRIPT_DIR/build-and-push-images.sh" "$ENV_FILE_ARG"
