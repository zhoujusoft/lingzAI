#!/bin/sh

IMAGE_TAG_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+$'
TARGET_PLATFORM_PATTERN='^linux/[A-Za-z0-9][A-Za-z0-9._/-]*$'

validate_image_tag() {
  tag="$1"
  printf '%s' "$tag" | grep -Eq "$IMAGE_TAG_PATTERN"
}

validate_target_platform() {
  platform="$1"
  printf '%s' "$platform" | grep -Eq "$TARGET_PLATFORM_PATTERN"
}

normalize_target_platform() {
  explicit_platform="${1:-}"
  if [ -z "$explicit_platform" ]; then
    printf '%s\n' "linux/amd64"
    return 0
  fi

  if ! validate_target_platform "$explicit_platform"; then
    echo "[release-meta] Invalid target platform: $explicit_platform" >&2
    echo "[release-meta] Expected format: linux/<arch>" >&2
    return 1
  fi

  printf '%s\n' "$explicit_platform"
}

platform_tag_suffix() {
  platform="$1"
  if [ "$platform" = "linux/amd64" ]; then
    printf '%s\n' ""
    return 0
  fi

  platform_token="${platform#linux/}"
  printf '%s\n' "-$(printf '%s' "$platform_token" | tr '/' '-')"
}

effective_image_tag() {
  base_tag="$1"
  target_platform="$2"
  suffix="$(platform_tag_suffix "$target_platform")"
  printf '%s%s\n' "$base_tag" "$suffix"
}

warn_if_non_default_platform_generates_variant_tag() {
  target_platform="$1"
  if [ "$target_platform" = "linux/amd64" ]; then
    return 0
  fi
  echo "[release-meta] Info: target platform '$target_platform' will produce a variant image tag suffix." >&2
}

pip_platform_for_target() {
  target_platform="$1"
  arch="${target_platform#linux/}"
  case "$arch" in
    amd64|x86_64) printf '%s\n' "manylinux2014_x86_64 manylinux_2_17_x86_64 manylinux_2_28_x86_64 linux_x86_64" ;;
    arm64|aarch64) printf '%s\n' "manylinux2014_aarch64 manylinux_2_17_aarch64 manylinux_2_27_aarch64 manylinux_2_28_aarch64 linux_aarch64" ;;
    *) printf '%s\n' "manylinux2014_${arch}" ;;
  esac
}
