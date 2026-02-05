# Foggy Dataset GraphQL

将 GraphQL 查询转换为 Foggy Dataset Model 的 JSON DSL，让 GraphQL 用户无缝使用 TM/QM 数据模型。

## 功能特性

✅ **GraphQL → DSL 自动转换**
- 字段选择 → `columns`
- 过滤条件 (`where`) → `slice`
- 排序 (`orderBy`) → `orderBy`
- 分页 (`limit/offset`) → `start/limit`

✅ **完整的 GraphQL 操作符支持**
- 比较：`_eq`, `_neq`, `_gt`, `_gte`, `_lt`, `_lte`
- 集合：`_in`, `_nin`
- 模糊匹配：`_like`, `_left_like`, `_right_like`
- 空值：`_is_null`
- 逻辑组合：`_or`, `_and`（支持无限嵌套）

✅ **DSL 特有功能支持**
- 范围操作符：`_range` (对应 `[]`, `[)`, `(]`, `()`)
- 层级查询：`_childrenOf`, `_descendantsOf`, `_selfAndDescendantsOf`
- 嵌套维度：自动处理多级维度引用

⚠️ **游标分页**（部分实现）
- 基础 `first`/`last` 参数已支持
- 完整的游标编解码待后续实现

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-graphql</artifactId>
    <version>8.1.4.beta</version>
</dependency>
```

### 2. 启用 GraphQL 支持

在 `application.yml` 中配置（默认已启用）：

```yaml
foggy:
  dataset:
    graphql:
      enabled: true
```

### 3. 发送 GraphQL 查询

```bash
POST http://localhost:8080/graphql
Content-Type: application/json

{
  "query": "query { factOrder(where: { orderStatus: { _eq: \"COMPLETED\" } }, limit: 10) { orderId totalAmount customer { name } } }"
}
```

## 使用示例

### 基础查询

**GraphQL**:
```graphql
query {
  factOrder {
    orderId
    orderStatus
    totalAmount
  }
}
```

**转换为 DSL**:
```json
{
  "page": 1,
  "pageSize": 10,
  "param": {
    "queryModel": "FactOrderQueryModel",
    "columns": ["orderId", "orderStatus", "totalAmount"]
  }
}
```

### 带过滤条件的查询

**GraphQL**:
```graphql
query {
  factOrder(
    where: {
      orderStatus: { _in: ["COMPLETED", "SHIPPED"] }
      totalAmount: { _gte: 100 }
    }
  ) {
    orderId
    totalAmount
  }
}
```

**转换为 DSL**:
```json
{
  "param": {
    "queryModel": "FactOrderQueryModel",
    "columns": ["orderId", "totalAmount"],
    "slice": [
      { "field": "orderStatus", "op": "in", "value": ["COMPLETED", "SHIPPED"] },
      { "field": "totalAmount", "op": ">=", "value": 100 }
    ]
  }
}
```

### OR 条件查询

**GraphQL**:
```graphql
query {
  factOrder(
    where: {
      _or: [
        { customer: { customerType: { _eq: "VIP" } } }
        { totalAmount: { _gte: 1000 } }
      ]
    }
  ) {
    orderId
  }
}
```

**转换为 DSL**:
```json
{
  "param": {
    "slice": [
      {
        "$or": [
          { "field": "customer$customerType", "op": "=", "value": "VIP" },
          { "field": "totalAmount", "op": ">=", "value": 1000 }
        ]
      }
    ]
  }
}
```

### 嵌套维度查询

**GraphQL**:
```graphql
query {
  factOrder {
    orderId
    customer {
      name
      customerType
    }
    product {
      name
      category {
        name
      }
    }
  }
}
```

**转换为 DSL**:
```json
{
  "param": {
    "columns": [
      "orderId",
      "customer$caption",
      "customer$customerType",
      "product$caption",
      "product.category$caption"
    ]
  }
}
```

### 排序和分页

**GraphQL**:
```graphql
query {
  factOrder(
    where: { orderStatus: { _eq: "COMPLETED" } }
    orderBy: [
      { totalAmount: desc }
      { orderId: asc }
    ]
    limit: 20
    offset: 40
  ) {
    orderId
    totalAmount
  }
}
```

**转换为 DSL**:
```json
{
  "start": 40,
  "limit": 20,
  "param": {
    "slice": [
      { "field": "orderStatus", "op": "=", "value": "COMPLETED" }
    ],
    "orderBy": [
      { "field": "totalAmount", "dir": "desc" },
      { "field": "orderId", "dir": "asc" }
    ]
  }
}
```

### 范围查询（DSL 特有）

**GraphQL**:
```graphql
query {
  factOrder(
    where: {
      orderDate: {
        _range: {
          from: "2024-01-01"
          to: "2024-07-01"
          fromInclusive: true
          toInclusive: false
        }
      }
    }
  ) {
    orderId
  }
}
```

**转换为 DSL**:
```json
{
  "param": {
    "slice": [
      {
        "field": "orderDate",
        "op": "[)",
        "value": ["2024-01-01", "2024-07-01"]
      }
    ]
  }
}
```

### 层级查询（父子维度）

**GraphQL**:
```graphql
query {
  factTeamSales(
    where: {
      team: {
        id: { _selfAndDescendantsOf: "T001" }
      }
    }
  ) {
    team {
      name
    }
    salesAmount
  }
}
```

**转换为 DSL**:
```json
{
  "param": {
    "columns": ["team$caption", "salesAmount"],
    "slice": [
      {
        "field": "team$id",
        "op": "selfAndDescendantsOf",
        "value": "T001"
      }
    ]
  }
}
```

## GraphQL 操作符映射表

| GraphQL 操作符 | DSL 操作符 | 说明 |
|---------------|-----------|------|
| `_eq` | `=` | 等于 |
| `_neq`, `_ne` | `!=` | 不等于 |
| `_gt` | `>` | 大于 |
| `_gte` | `>=` | 大于等于 |
| `_lt` | `<` | 小于 |
| `_lte` | `<=` | 小于等于 |
| `_in` | `in` | 包含于 |
| `_nin` | `not in` | 不包含于 |
| `_like` | `like` | 模糊匹配 |
| `_left_like` | `left_like` | 左匹配 |
| `_right_like` | `right_like` | 右匹配 |
| `_is_null` | `is null` / `is not null` | 空值判断 |
| `_range` | `[]`, `[)`, `(]`, `()` | 范围查询 |
| `_childrenOf` | `childrenOf` | 直接子节点 |
| `_descendantsOf` | `descendantsOf` | 所有后代节点 |
| `_selfAndDescendantsOf` | `selfAndDescendantsOf` | 自身及后代 |

## 字段名映射规则

| GraphQL 字段 | DSL 字段 | 说明 |
|-------------|---------|------|
| `name` | `$caption` | 维度显示名称 |
| `id` | `$id` | 维度 ID |
| `customer { name }` | `customer$caption` | 一级维度 |
| `product { category { name } }` | `product.category$caption` | 嵌套维度 |

## 编程式使用

除了 HTTP 端点，也可以在代码中直接使用转换器：

```java
@Resource
private GraphqlToDslConverter converter;

@Resource
private QueryFacade queryFacade;

public void executeGraphqlQuery(String graphqlQuery) {
    // 转换 GraphQL → DSL
    PagingRequest<DbQueryRequestDef> dslRequest = converter.convert(
        graphqlQuery,
        new HashMap<>()
    );

    // 执行查询
    PagingResultImpl result = queryFacade.queryModelData(dslRequest);

    // 处理结果
    List items = result.getItems();
}
```

## 配置选项

```yaml
foggy:
  dataset:
    graphql:
      # 是否启用 GraphQL 支持（默认 true）
      enabled: true

      # 是否启用转换器 Bean（默认 true）
      converter:
        enabled: true
```

## 限制和未来计划

### 当前限制

1. **游标分页**：`after`/`before` 参数暂未完整实现，需要游标编解码器
2. **聚合查询**：`_aggregate` 语法待实现（需要映射到 `groupBy`）
3. **Relay Connection**：响应格式暂时简化，未完整实现 `edges`/`node`/`pageInfo` 包装
4. **GraphQL Schema**：暂未实现从 TM/QM 自动生成 Schema（需手动编写 GraphQL 查询）

### 未来计划

- [ ] 完整的游标分页支持（游标编解码器）
- [ ] 聚合查询支持（`_aggregate` 语法）
- [ ] Relay Connection 响应格式
- [ ] 从 TM/QM 自动生成 GraphQL Schema
- [ ] GraphQL Playground 集成
- [ ] 订阅（Subscription）支持

## 常见问题

### Q: 如何指定查询模型名称？

A: 查询模型名称从 GraphQL 字段名自动推断。例如：
- `factOrder` → `FactOrderQueryModel`
- `customerSales` → `CustomerSalesQueryModel`

### Q: 支持查询变量吗？

A: 支持。发送请求时传入 `variables` 字段：

```json
{
  "query": "query($status: String!) { factOrder(where: { orderStatus: { _eq: $status } }) { orderId } }",
  "variables": { "status": "COMPLETED" }
}
```

### Q: 如何处理复杂的嵌套条件？

A: 使用 `_or` 和 `_and` 可以无限嵌套：

```graphql
where: {
  _or: [
    {
      _and: [
        { field1: { _eq: "A" } }
        { field2: { _gt: 100 } }
      ]
    }
    { field3: { _eq: "B" } }
  ]
}
```

## 许可证

Apache License 2.0

## 贡献

欢迎提交 Issue 和 Pull Request！
