#!/bin/bash
# 打包 Release 资源
# 用法: ./build-release.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

VERSION="8.1.8-beta"
RELEASE_DIR="release"

echo "=== Building Release Assets ==="

# 清理
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"

# 1. 打包 Odoo 插件
echo "Packing foggy-odoo-addon.zip..."
zip -r "$RELEASE_DIR/foggy-odoo-addon.zip" foggy_mcp/

# 2. 复制 docker-compose.yml
echo "Copying docker-compose.yml..."
cp docker-compose.yml "$RELEASE_DIR/"

# 4. 复制 SQL 文件（可选，供手动初始化）
echo "Copying SQL files..."
mkdir -p "$RELEASE_DIR/sql"
cp foggy_mcp/setup/sql/refresh_closure_tables.sql "$RELEASE_DIR/sql/"

echo ""
echo "=== Release Assets Ready ==="
ls -lh "$RELEASE_DIR/"

echo ""
echo "Upload these files to GitHub Release:"
echo "  https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/new"
echo ""
echo "Files:"
echo "  - foggy-odoo-addon.zip"
echo "  - docker-compose.yml"
echo "  - sql/refresh_closure_tables.sql (optional)"