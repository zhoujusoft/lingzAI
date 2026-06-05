#!/bin/bash
set -e

usage() {
    echo "用法: $0 <源amd64完整镜像>"
    echo "示例: $0 125.75.152.167:5001/lingzhou-frontend:1.7.0"
    exit 1
}
[ $# -ne 1 ] && usage

SRC_IMG="$1"
REPO=$(echo "$SRC_IMG" | cut -d'/' -f1)
NAME_TAG=$(echo "$SRC_IMG" | cut -d'/' -f2)
IMG_NAME=${NAME_TAG%%:*}
TAG=${NAME_TAG#*:}

DST_IMG="${REPO}/${IMG_NAME}_arm:${TAG}"
TAR_FILE="${IMG_NAME}_arm_${TAG}.tar"

echo "源镜像:$SRC_IMG"
echo "ARM镜像:$DST_IMG"
echo "打包文件:./$TAR_FILE"

# 1.拉取原x86镜像
docker pull --platform amd64 "$SRC_IMG"

# 2.安装qemu跨架构
docker run --privileged --rm tonistiigi/binfmt --install all >/dev/null

# ==========关键方案：先用docker save加载本地镜像为tar，再docker import指定arm64架构，完全不走FROM拉取=========
TMP_RAW=$(mktemp /tmp/raw.XXXX.tar)
# 导出本地amd64镜像到临时文件
docker save -o "$TMP_RAW" "$SRC_IMG"
# 导入并手动指定架构为arm64，生成arm镜像
cat "$TMP_RAW" | docker import --platform=linux/arm64 - "$DST_IMG"
# 删除临时raw包
rm -f "$TMP_RAW"

# 导出最终arm镜像tar
docker save -o "$TAR_FILE" "$DST_IMG"

# 清理无用缓存
docker image prune -f

echo -e "\n✅ 转换完成！$TAR_FILE"
echo "ARM服务器导入: docker load -i $TAR_FILE"
