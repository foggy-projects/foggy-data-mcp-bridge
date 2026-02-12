# 保存查询功能测试指南

## 前置条件

1. 启动 demo 应用：`mvn spring-boot:run -pl foggy-dataset-demo`
2. 确保 MongoDB 已启动（通过 docker-compose）
3. `DemoSecurityIdentityResolver` 已自动注册为 Spring Bean

## 测试流程

### 1. 验证身份解析器工作正常

```bash
# 测试 manager 身份
curl -X GET "http://localhost:8080/test/identity" \
  -H "Authorization: Bearer manager-token-123"

# 预期响应：
# {
#   "code": 200,
#   "data": {
#     "userId": "user_manager_001",
#     "deptId": "dept_sales",
#     "tenantId": "tenant_demo",
#     "attributes": {
#       "role": "MANAGER",
#       "storeKey": "1",
#       "userName": "张三（经理）"
#     }
#   }
# }

# 测试 analyst 身份
curl -X GET "http://localhost:8080/test/identity" \
  -H "Authorization: Bearer analyst-token-456"

# 测试 admin 身份
curl -X GET "http://localhost:8080/test/identity" \
  -H "Authorization: Bearer admin-token-789"
```

### 2. 创建查询（获取 queryId）

```bash
curl -X POST "http://localhost:8080/data-viewer/api/query/create" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "FactSalesDemoAuthQueryModel",
    "title": "测试查询",
    "payload": {
      "columns": ["orderId", "orderDate", "customer", "product", "salesAmount"],
      "slice": [
        {"field": "orderDate", "op": ">=", "value": "2024-01-01"}
      ],
      "orderBy": [
        {"field": "orderDate", "order": "desc"}
      ]
    }
  }'

# 记录返回的 queryId，例如：4adc9b2d811f45ae
```

### 3. 保存查询（个人私有）

```bash
curl -X POST "http://localhost:8080/data-viewer/api/saved-query" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer manager-token-123" \
  -d '{
    "model": "FactSalesDemoAuthQueryModel",
    "title": "我的销售明细查询",
    "description": "查看2024年以来的销售数据",
    "columns": ["orderId", "orderDate", "customer", "product", "salesAmount"],
    "slice": [
      {"field": "orderDate", "op": ">=", "value": "2024-01-01"}
    ],
    "orderBy": [
      {"field": "orderDate", "order": "desc"}
    ],
    "visibility": "PRIVATE"
  }'

# 预期响应：
# {
#   "code": 200,
#   "data": {
#     "id": "saved-query-id-001",
#     "model": "FactSalesDemoAuthQueryModel",
#     "title": "我的销售明细查询",
#     "ownerId": "user_manager_001",
#     "ownerDeptId": "dept_sales",
#     "visibility": "PRIVATE",
#     ...
#   }
# }
```

### 4. 保存查询（部门共享）

```bash
curl -X POST "http://localhost:8080/data-viewer/api/saved-query" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer manager-token-123" \
  -d '{
    "model": "FactSalesDemoAuthQueryModel",
    "title": "销售部门共享查询",
    "description": "部门内所有人可见",
    "columns": ["orderId", "orderDate", "customer", "salesAmount"],
    "slice": [],
    "visibility": "DEPARTMENT"
  }'
```

### 5. 保存查询（租户共享）

```bash
curl -X POST "http://localhost:8080/data-viewer/api/saved-query" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer admin-token-789" \
  -d '{
    "model": "FactSalesDemoAuthQueryModel",
    "title": "全公司可见查询",
    "description": "租户内所有人可见",
    "columns": ["orderId", "orderDate", "salesAmount"],
    "slice": [],
    "visibility": "TENANT"
  }'
```

### 6. 列出可见查询

```bash
# manager 身份列出查询（应看到：自己的私有查询 + 销售部门查询 + 租户查询）
curl -X GET "http://localhost:8080/data-viewer/api/saved-query/list/FactSalesDemoAuthQueryModel" \
  -H "Authorization: Bearer manager-token-123"

# analyst 身份列出查询（应看到：自己的私有查询 + 分析部门查询 + 租户查询）
curl -X GET "http://localhost:8080/data-viewer/api/saved-query/list/FactSalesDemoAuthQueryModel" \
  -H "Authorization: Bearer analyst-token-456"
```

### 7. 获取查询详情

```bash
curl -X GET "http://localhost:8080/data-viewer/api/saved-query/{saved-query-id}" \
  -H "Authorization: Bearer manager-token-123"
```

### 8. 更新查询（仅 owner）

```bash
curl -X PUT "http://localhost:8080/data-viewer/api/saved-query/{saved-query-id}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer manager-token-123" \
  -d '{
    "model": "FactSalesDemoAuthQueryModel",
    "title": "我的销售明细查询（已更新）",
    "description": "更新描述",
    "columns": ["orderId", "orderDate", "customer", "product", "salesAmount", "profit"],
    "slice": [
      {"field": "orderDate", "op": ">=", "value": "2024-01-01"}
    ],
    "visibility": "PRIVATE"
  }'
```

### 9. 应用保存的查询

```bash
# 应用保存的查询，创建临时 cached_query
curl -X POST "http://localhost:8080/data-viewer/api/saved-query/{saved-query-id}/apply" \
  -H "Authorization: Bearer manager-token-123"

# 预期响应：
# {
#   "code": 200,
#   "data": {
#     "queryId": "4adc9b2d811f45ae"
#   }
# }

# 然后使用返回的 queryId 查询数据
curl -X POST "http://localhost:8080/data-viewer/api/query/FactSalesDemoAuthQueryModel/4adc9b2d811f45ae/data" \
  -H "Content-Type: application/json" \
  -d '{
    "start": 0,
    "limit": 10,
    "slice": [],
    "orderBy": []
  }'
```

### 10. 删除查询（仅 owner）

```bash
curl -X DELETE "http://localhost:8080/data-viewer/api/saved-query/{saved-query-id}" \
  -H "Authorization: Bearer manager-token-123"
```

## 验证权限隔离

### 测试 1：非 owner 无法更新查询

```bash
# manager 创建的查询
SAVED_ID=$(curl -X POST "http://localhost:8080/data-viewer/api/saved-query" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer manager-token-123" \
  -d '{"model":"FactSalesDemoAuthQueryModel","title":"Manager查询","columns":["orderId"],"visibility":"PRIVATE"}' \
  | jq -r '.data.id')

# analyst 尝试更新（应失败）
curl -X PUT "http://localhost:8080/data-viewer/api/saved-query/${SAVED_ID}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer analyst-token-456" \
  -d '{"model":"FactSalesDemoAuthQueryModel","title":"被篡改","columns":["orderId"]}'

# 预期返回：403 Forbidden 或 400 权限错误
```

### 测试 2：部门隔离

```bash
# sales 部门的 manager 创建部门共享查询
curl -X POST "http://localhost:8080/data-viewer/api/saved-query" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer manager-token-123" \
  -d '{"model":"FactSalesDemoAuthQueryModel","title":"销售部门查询","columns":["orderId"],"visibility":"DEPARTMENT"}'

# analytics 部门的 analyst 列出查询（应看不到 sales 部门的查询）
curl -X GET "http://localhost:8080/data-viewer/api/saved-query/list/FactSalesDemoAuthQueryModel" \
  -H "Authorization: Bearer analyst-token-456"
```

### 测试 3：租户级别可见

```bash
# admin 创建租户级查询
curl -X POST "http://localhost:8080/data-viewer/api/saved-query" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer admin-token-789" \
  -d '{"model":"FactSalesDemoAuthQueryModel","title":"全公司查询","columns":["orderId"],"visibility":"TENANT"}'

# 所有用户（manager, analyst, admin）都应该能看到
curl -X GET "http://localhost:8080/data-viewer/api/saved-query/list/FactSalesDemoAuthQueryModel" \
  -H "Authorization: Bearer manager-token-123"
```

## 故障排查

### 问题 1：503 Service Unavailable

**原因**：`SecurityIdentityResolver` 未注册为 Spring Bean

**解决**：确认 `DemoSecurityIdentityResolver` 有 `@Component` 注解，并在 Spring 扫描路径下

### 问题 2：401 Unauthorized

**原因**：缺少 `Authorization` header

**解决**：所有保存查询相关请求必须携带 `Authorization` header

### 问题 3：MongoDB 连接失败

**原因**：MongoDB 未启动

**解决**：
```bash
cd foggy-dataset-demo/docker
docker-compose up -d mongodb
```

## 预期结果

1. ✅ 身份解析正常工作（/test/identity 返回用户信息）
2. ✅ 保存查询成功（返回 saved query 对象）
3. ✅ 列表查询按可见性过滤（PRIVATE/DEPARTMENT/TENANT）
4. ✅ 非 owner 无法更新/删除他人查询
5. ✅ apply 功能正常（保存的查询 → cached query → 执行查询）
