#!/bin/bash

# 保存查询功能快速测试脚本
# 使用方法：./test-saved-query.sh

BASE_URL="http://localhost:8080"

echo "=========================================="
echo " 保存查询功能测试"
echo "=========================================="
echo ""

# 测试 1: 验证身份解析
echo "✅ 测试 1: 验证身份解析"
echo "请求: GET /test/identity (Manager)"
RESULT=$(curl -s -X GET "${BASE_URL}/test/identity" \
  -H "Authorization: Bearer manager-token-123")
echo "$RESULT" | jq '.'
echo ""

# 测试 2: 创建临时查询
echo "✅ 测试 2: 创建临时查询"
CREATE_RESULT=$(curl -s -X POST "${BASE_URL}/data-viewer/api/query/create" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "FactSalesDemoAuthQueryModel",
    "title": "测试查询",
    "payload": {
      "columns": ["orderId", "orderDate", "customer", "product", "salesAmount"],
      "slice": [{"field": "orderDate", "op": ">=", "value": "2024-01-01"}]
    }
  }')
echo "$CREATE_RESULT" | jq '.'
QUERY_ID=$(echo "$CREATE_RESULT" | jq -r '.data.queryId')
echo "获取到 queryId: $QUERY_ID"
echo ""

# 测试 3: 保存查询（个人私有）
echo "✅ 测试 3: 保存查询（个人私有）"
SAVE_RESULT=$(curl -s -X POST "${BASE_URL}/data-viewer/api/saved-query" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer manager-token-123" \
  -d '{
    "model": "FactSalesDemoAuthQueryModel",
    "title": "我的销售明细查询",
    "description": "查看2024年以来的销售数据",
    "columns": ["orderId", "orderDate", "customer", "product", "salesAmount"],
    "slice": [{"field": "orderDate", "op": ">=", "value": "2024-01-01"}],
    "orderBy": [{"field": "orderDate", "order": "desc"}],
    "visibility": "PRIVATE"
  }')
echo "$SAVE_RESULT" | jq '.'
SAVED_ID=$(echo "$SAVE_RESULT" | jq -r '.data.id')
echo "保存的查询 ID: $SAVED_ID"
echo ""

# 测试 4: 保存查询（部门共享）
echo "✅ 测试 4: 保存查询（部门共享）"
DEPT_RESULT=$(curl -s -X POST "${BASE_URL}/data-viewer/api/saved-query" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer manager-token-123" \
  -d '{
    "model": "FactSalesDemoAuthQueryModel",
    "title": "销售部门共享查询",
    "description": "部门内所有人可见",
    "columns": ["orderId", "orderDate", "customer", "salesAmount"],
    "visibility": "DEPARTMENT"
  }')
echo "$DEPT_RESULT" | jq '.'
DEPT_ID=$(echo "$DEPT_RESULT" | jq -r '.data.id')
echo ""

# 测试 5: 保存查询（租户共享）
echo "✅ 测试 5: 保存查询（租户共享）"
TENANT_RESULT=$(curl -s -X POST "${BASE_URL}/data-viewer/api/saved-query" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer admin-token-789" \
  -d '{
    "model": "FactSalesDemoAuthQueryModel",
    "title": "全公司可见查询",
    "description": "租户内所有人可见",
    "columns": ["orderId", "orderDate", "salesAmount"],
    "visibility": "TENANT"
  }')
echo "$TENANT_RESULT" | jq '.'
TENANT_ID=$(echo "$TENANT_RESULT" | jq -r '.data.id')
echo ""

# 测试 6: 列出可见查询（Manager 视角）
echo "✅ 测试 6: 列出可见查询（Manager 视角）"
echo "应看到: 个人私有 + 销售部门 + 租户查询"
LIST_MANAGER=$(curl -s -X GET "${BASE_URL}/data-viewer/api/saved-query/list/FactSalesDemoAuthQueryModel" \
  -H "Authorization: Bearer manager-token-123")
echo "$LIST_MANAGER" | jq '.data | length as $count | "共 \($count) 个查询"'
echo "$LIST_MANAGER" | jq '.data[] | {id, title, visibility, ownerId}'
echo ""

# 测试 7: 列出可见查询（Analyst 视角）
echo "✅ 测试 7: 列出可见查询（Analyst 视角）"
echo "应看到: 租户查询（不应看到 Manager 的个人查询和销售部门查询）"
LIST_ANALYST=$(curl -s -X GET "${BASE_URL}/data-viewer/api/saved-query/list/FactSalesDemoAuthQueryModel" \
  -H "Authorization: Bearer analyst-token-456")
echo "$LIST_ANALYST" | jq '.data | length as $count | "共 \($count) 个查询"'
echo "$LIST_ANALYST" | jq '.data[] | {id, title, visibility, ownerId}'
echo ""

# 测试 8: 应用保存的查询
if [ ! -z "$SAVED_ID" ] && [ "$SAVED_ID" != "null" ]; then
  echo "✅ 测试 8: 应用保存的查询"
  APPLY_RESULT=$(curl -s -X POST "${BASE_URL}/data-viewer/api/saved-query/${SAVED_ID}/apply" \
    -H "Authorization: Bearer manager-token-123")
  echo "$APPLY_RESULT" | jq '.'
  APPLIED_QUERY_ID=$(echo "$APPLY_RESULT" | jq -r '.data.queryId')
  echo "生成的临时 queryId: $APPLIED_QUERY_ID"
  echo ""
fi

# 测试 9: 权限测试（非 owner 尝试更新）
if [ ! -z "$SAVED_ID" ] && [ "$SAVED_ID" != "null" ]; then
  echo "✅ 测试 9: 权限测试（Analyst 尝试更新 Manager 的查询）"
  echo "预期: 应该失败（403 或权限错误）"
  PERM_RESULT=$(curl -s -X PUT "${BASE_URL}/data-viewer/api/saved-query/${SAVED_ID}" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer analyst-token-456" \
    -d '{
      "model": "FactSalesDemoAuthQueryModel",
      "title": "被篡改的标题",
      "columns": ["orderId"]
    }')
  echo "$PERM_RESULT" | jq '.'
  echo ""
fi

# 测试 10: 删除查询
if [ ! -z "$SAVED_ID" ] && [ "$SAVED_ID" != "null" ]; then
  echo "✅ 测试 10: 删除查询"
  DELETE_RESULT=$(curl -s -X DELETE "${BASE_URL}/data-viewer/api/saved-query/${SAVED_ID}" \
    -H "Authorization: Bearer manager-token-123")
  echo "$DELETE_RESULT" | jq '.'
  echo ""
fi

echo "=========================================="
echo " 测试完成！"
echo "=========================================="
