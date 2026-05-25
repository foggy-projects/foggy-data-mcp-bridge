#!/bin/bash
#
# publish.sh — 发布 foggy-data-viewer 到 npm registry
#
# 用法：
#   ./scripts/publish.sh              # 发布 beta 版
#   ./scripts/publish.sh --latest     # 发布正式版
#   ./scripts/publish.sh --dry-run    # 仅预览，不实际发布
#

set -e
cd "$(dirname "$0")/.."

# 加载 .env 中的 NPM_TOKEN
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

if [ -z "$NPM_TOKEN" ]; then
  echo "ERROR: NPM_TOKEN not found. Create .env with NPM_TOKEN=xxx"
  exit 1
fi

# 解析参数
TAG="beta"
DRY_RUN=""
for arg in "$@"; do
  case $arg in
    --latest) TAG="latest" ;;
    --dry-run) DRY_RUN="--dry-run" ;;
  esac
done

echo "=== foggy-data-viewer publish ==="
echo "  Version: $(node -p 'require("./package.json").version')"
echo "  Tag:     $TAG"
echo "  Registry: https://registry.npmjs.org"
[ -n "$DRY_RUN" ] && echo "  Mode:    DRY RUN"
echo ""

# 测试与构建 lib 模式
echo "Testing..."
npm test
echo ""

echo "Building lib..."
npm run build:lib
echo ""

# 发布
echo "Publishing..."
npm publish \
  --tag "$TAG" \
  --access public \
  --ignore-scripts \
  --registry https://registry.npmjs.org \
  --//registry.npmjs.org/:_authToken="$NPM_TOKEN" \
  $DRY_RUN

echo ""
echo "Done. Published foggy-data-viewer@$(node -p 'require("./package.json").version') with tag=$TAG"
