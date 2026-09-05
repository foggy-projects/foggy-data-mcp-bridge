# API 开发规范

## 统一返回格式 (RX)

项目使用 `RX` 类作为统一的 API 响应格式，位于 `foggy-core` 模块。

### RX 响应结构

```json
{
  "code": 200,          // 状态码：200=成功, 其他=错误码
  "msg": "错误信息",     // 可选，错误或提示消息
  "data": { ... }       // 业务数据
}
```

### 后端使用规范

**成功响应：**
```java
// 返回数据
return RX.ok(data);

// 无数据返回
return RX.ok();

// 404 Not Found
return RX.notFound().build();
```

**错误响应：**
```java
// 业务错误（600错误码）
return RX.failB("错误信息", errorData);

// 自定义状态码（如410 Gone）
return new RX<>(410, null, "Query expired", expiredData);

// 通用错误
return RX.error("错误信息");
```

### 前端处理规范

前端需要从 `response.data` 中解析 RX 结构：

```typescript
const response = await axios.get('/api/endpoint')

// 检查 RX code 状态（200 表示成功）
if (!response.data || response.data.code !== 200) {
  throw new Error(response.data?.msg || '请求失败')
}

// 提取业务数据
const data = response.data.data
```

### 重要说明

- **禁止使用 `ResponseEntity`**：新的 REST API 必须使用 RX 返回
- **一般业务错误返回 200**：通常通过 RX.code 判断业务状态；输入安全边界等明确约定的 fail-closed 校验可返回 HTTP 4xx，例如 Query DSL strict/受保护未知属性返回 400
- **data 可选**：成功时 data 包含业务数据，错误时 data 可包含错误详情
- **一致性**：所有模块的 REST API 应遵循此规范

### 示例：foggy-data-viewer

```java
@GetMapping("/query/{id}/meta")
public RX<QueryMetaResponse> getQueryMeta(@PathVariable String id) {
    return cacheService.getQuery(id)
        .map(ctx -> RX.ok(new QueryMetaResponse(...)))
        .orElse(RX.notFound().build());
}
```

## Semantic Query Debug Contract

`SemanticQueryResponse.debug` is optional diagnostic data. Normal business
clients must not depend on it for result correctness, but AI/MCP callers may use
it to explain query planning and routing decisions.

`debug.extra.aggregateRelationDiagnostics` is an optional array emitted when the
Java engine plans an aggregate relation query and has filter-handling evidence.
Each item has this stable shape:

```json
{
  "decision": "pushed",
  "reasonCode": null,
  "field": "salesAmount",
  "op": "[]",
  "target": "having",
  "expression": "sum(agg_src.sales_amount) >= ?"
}
```

Field meanings:

- `decision`: `pushed`, `retained`, or `refused`.
- `target`: `where`, `having`, or `outer`; present when the planner can name
  where the predicate is applied.
- `reasonCode`: stable refusal or retention reason when a predicate is not
  pushed, for example `OR_CONDITION_OUTER_ONLY`, `NULL_CHECK_OUTER_ONLY`, or
  `UNSUPPORTED_OPERATOR`.
- `field` and `op`: the semantic field and request operator that triggered the
  decision.
- `expression`: SQL fragment evidence for pushed predicates only. It is
  diagnostic text, not an executable SQL API.

AI/MCP consumers should treat `pushed` as planner evidence that a safe RHS
aggregate `WHERE` / `HAVING` duplicate was created. `retained` means the outer
filter is intentionally kept as the authority predicate. `refused` means the
predicate shape was not copied into the aggregate relation because it crossed a
defined safety boundary.

## RX 类 API 参考

### 静态工厂方法

- `RX.ok()` / `RX.ok(T data)` - 成功响应（code=200）
- `RX.success(T data)` - 同 ok()
- `RX.error(String msg)` - 通用错误（code=600）
- `RX.failA(String msg)` - 用户端错误（A600）
- `RX.failB(String msg)` - 业务端错误（B600）
- `RX.failC(String msg)` - 第三方错误（C600）
- `RX.notFound()` - 404 错误
- `RX.status(HttpStatusCode code)` - 自定义状态码

### 构建器模式

```java
RX.status(HttpStatus.SERVICE_UNAVAILABLE)
    .msg("服务不可用")
    .build();
```

### 直接构造

```java
new RX<>(code, exCode, msg, data)
```
