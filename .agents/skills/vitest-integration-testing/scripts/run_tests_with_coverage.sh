#!/bin/bash
# 运行 Vitest 测试并生成覆盖率报告
#
# 用法:
#   bash run_tests_with_coverage.sh [options]
#
# 选项:
#   --watch       监听模式运行
#   --ui          打开 Vitest UI
#   --reporter    指定覆盖率报告格式 (text|html|json|lcov)
#   --threshold   设置覆盖率阈值 (默认: 80)
#   --help        显示此帮助信息
#
# 示例:
#   bash run_tests_with_coverage.sh
#   bash run_tests_with_coverage.sh --watch
#   bash run_tests_with_coverage.sh --reporter html --threshold 85
#   bash run_tests_with_coverage.sh --ui

set -e

# 默认配置
WATCH_MODE=false
UI_MODE=false
REPORTER="text,html"
THRESHOLD=80

# 解析命令行参数
while [[ $# -gt 0 ]]; do
  case $1 in
    --watch)
      WATCH_MODE=true
      shift
      ;;
    --ui)
      UI_MODE=true
      shift
      ;;
    --reporter)
      REPORTER="$2"
      shift 2
      ;;
    --threshold)
      THRESHOLD="$2"
      shift 2
      ;;
    --help)
      grep "^#" "$0" | sed 's/^# //' | sed 's/^#//'
      exit 0
      ;;
    *)
      echo "未知选项: $1"
      echo "使用 --help 查看帮助"
      exit 1
      ;;
  esac
done

echo "🧪 运行 Vitest 测试..."
echo "📊 覆盖率报告: $REPORTER"
echo "🎯 覆盖率阈值: ${THRESHOLD}%"
echo ""

# 构建 vitest 命令
CMD="npx vitest"

if [ "$WATCH_MODE" = true ]; then
  CMD="$CMD watch"
elif [ "$UI_MODE" = true ]; then
  CMD="$CMD --ui"
else
  CMD="$CMD run"
fi

# 添加覆盖率配置
CMD="$CMD --coverage"

# 设置覆盖率阈值
export VITEST_COVERAGE_LINES=$THRESHOLD
export VITEST_COVERAGE_FUNCTIONS=$THRESHOLD
export VITEST_COVERAGE_BRANCHES=$((THRESHOLD - 5))
export VITEST_COVERAGE_STATEMENTS=$THRESHOLD

# 执行测试
eval $CMD

# 检查退出码
EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
  echo ""
  echo "✅ 测试通过！"
  echo "📁 覆盖率报告已生成: coverage/index.html"
else
  echo ""
  echo "❌ 测试失败或覆盖率未达标"
  exit $EXIT_CODE
fi
