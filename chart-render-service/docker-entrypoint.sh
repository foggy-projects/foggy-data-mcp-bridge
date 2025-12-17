#!/bin/bash
# Docker启动入口脚本 - 自动安装依赖

set -e

echo "=========================================="
echo "Chart Render Service 启动中..."
echo "=========================================="

# 检查node_modules是否存在
if [ ! -d "node_modules" ] || [ ! -f "node_modules/.installed" ]; then
    echo "[INFO] 未检测到node_modules，开始安装依赖..."
    echo "[INFO] 使用npm镜像: $(npm config get registry)"

    # 安装生产依赖
    npm ci --only=production

    # 创建标记文件
    touch node_modules/.installed

    echo "[SUCCESS] 依赖安装完成"
else
    echo "[INFO] 检测到已安装的依赖，跳过安装"
fi

# 显示环境信息
echo "=========================================="
echo "Node.js版本: $(node -v)"
echo "npm版本: $(npm -v)"
echo "工作目录: $(pwd)"
echo "启动命令: $@"
echo "=========================================="

# 执行传入的命令
exec "$@"