#!/bin/bash
# ============================================
# Docker Hub 镜像检查脚本
# 检查 chart-render-service 镜像是否已发布
# ============================================

# 可能的镜像名称列表
POSSIBLE_IMAGES=(
    "foggysource/chart-render-service"
)

echo "=========================================="
echo "检查 Docker Hub 上的 chart-render-service 镜像"
echo "=========================================="
echo ""

check_image() {
    local image=$1
    echo "检查: $image"

    # 使用 Docker Hub API 检查镜像
    response=$(curl -s "https://hub.docker.com/v2/repositories/$image/tags?page_size=10" 2>/dev/null)

    if echo "$response" | grep -q '"results"'; then
        echo "  ✅ 镜像存在!"
        echo "  可用 tags:"
        echo "$response" | grep -o '"name":"[^"]*"' | sed 's/"name":"//g' | sed 's/"//g' | while read tag; do
            echo "    - $tag"
        done
        echo ""
        return 0
    else
        echo "  ❌ 镜像不存在或无法访问"
        echo ""
        return 1
    fi
}

found=false
for image in "${POSSIBLE_IMAGES[@]}"; do
    if check_image "$image"; then
        found=true
        echo "=========================================="
        echo "推荐使用的镜像: $image"
        echo "=========================================="
        break
    fi
done

if [ "$found" = false ]; then
    echo "=========================================="
    echo "未找到已发布的镜像"
    echo ""
    echo "如果你已发布到其他名称，请手动检查:"
    echo "  curl https://hub.docker.com/v2/repositories/YOUR_USERNAME/chart-render-service/tags"
    echo "=========================================="
fi
