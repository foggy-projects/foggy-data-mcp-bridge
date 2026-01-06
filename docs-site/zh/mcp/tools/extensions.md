# 扩展工具

Foggy MCP 支持通过扩展模块添加额外工具。这些工具需要额外的依赖和配置才能启用。

## Data Viewer

Data Viewer 是一个数据浏览器扩展，提供 `dataset.open_in_viewer` 工具，用于生成可分享的浏览器链接，支持交互式浏览大数据集。

### 启用条件

Data Viewer 需要以下依赖：

1. **MongoDB** - 用于缓存查询上下文
2. **foggy-data-viewer 模块** - 包含在 `foggy-mcp-launcher` 中

### 配置

确保 MongoDB 已启动并配置：

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/foggy_test
      auto-index-creation: true

# 可选：禁用 Data Viewer
foggy:
  data-viewer:
    enabled: true  # 默认 true
    base-url: http://localhost:7108/data-viewer
```

启动 MongoDB（使用 demo docker）：

```bash
cd foggy-dataset-demo/docker
docker-compose up -d mongo
```

### 验证

启动服务后，调用 `tools/list` 检查工具是否可用：

```bash
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}'
```

如果 `dataset.open_in_viewer` 出现在列表中，说明扩展已启用。

---

## dataset.open_in_viewer

生成可分享的浏览器链接，用于交互式浏览大数据集。

### 使用场景

**适用：**
- 明细数据查询，预期返回大量行（500+）
- 用户要求"全部"、"列表"、"导出"类查询
- 需要交互式探索（过滤、排序、分页）

**不适用（改用 dataset.query_model）：**
- 带 groupBy 的聚合查询（结果集小）
- 明确限制 ≤100 行的查询
- AI 需要直接分析数据内容

### 参数

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `model` | string | ✅ | 查询模型名称 |
| `payload` | object | ✅ | 查询参数（与 `query_model` 格式一致） |
| `payload.columns` | array | ✅ | 要显示的列列表 |
| `payload.slice` | array | ✅ | 过滤条件（至少一个） |
| `payload.orderBy` | array | ❌ | 排序配置 |
| `payload.groupBy` | array | ❌ | 分组/聚合字段 |
| `title` | string | ❌ | 数据视图的标题 |

> **重要**：`payload.slice` 必须提供至少一个过滤条件，防止无界查询。

### 调用示例

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "dataset.open_in_viewer",
    "arguments": {
      "model": "FactOrderQueryModel",
      "payload": {
        "columns": ["orderNo", "customer$caption", "orderDate$caption", "totalAmount"],
        "slice": [
          {"field": "orderDate$id", "op": "[)", "value": ["20250101", "20250131"]}
        ],
        "orderBy": [
          {"field": "orderDate$id", "dir": "DESC"}
        ]
      },
      "title": "2025年1月订单"
    }
  }
}
```

### 返回值

```json
{
  "viewerUrl": "http://localhost:7108/data-viewer/view/abc123",
  "queryId": "abc123",
  "expiresAt": "2025-01-06T10:00:00",
  "estimatedRowCount": 1500,
  "message": "Data viewer link created. The link expires at 2025-01-06T10:00:00. Users can browse, filter, sort, and export the data interactively."
}
```

| 字段 | 说明 |
|------|------|
| `viewerUrl` | 浏览器访问链接 |
| `queryId` | 查询缓存 ID |
| `expiresAt` | 链接过期时间 |
| `estimatedRowCount` | 预估行数（可选） |

### 权限

| 端点 | 可用 |
|------|:----:|
| `/mcp/admin/rpc` | ✅ |
| `/mcp/analyst/rpc` | ✅ |
| `/mcp/business/rpc` | ❌ |

### 与 query_model 的区别

| 特性 | query_model | open_in_viewer |
|------|-------------|----------------|
| 返回数据 | 直接返回查询结果 | 返回浏览器链接 |
| 适合数据量 | 小（≤100行） | 大（500+行） |
| AI 分析 | ✅ AI 可直接分析 | ❌ 需用户在浏览器查看 |
| 交互能力 | ❌ 静态结果 | ✅ 过滤/排序/分页/导出 |
| 过滤条件 | 可选 | 必需（至少一个） |

### 典型工作流

```
用户: "导出上个月的所有订单"

AI 判断:
1. "所有订单" → 数据量可能很大
2. 用户要求"导出" → 需要交互式浏览
3. 选择 open_in_viewer 而非 query_model

AI 调用 open_in_viewer:
→ 返回浏览器链接
→ 用户可在浏览器中查看、筛选、导出
```

---

## 开发自定义扩展

如需开发自定义 MCP 工具扩展，请参考 `foggy-data-viewer` 模块的实现：

### 1. 创建工具类

实现 `McpTool` 接口（来自 `foggy-mcp-spi`）：

```java
public class MyCustomTool implements McpTool {

    @Override
    public String getName() {
        return "my.custom_tool";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        return Set.of(ToolCategory.QUERY);
    }

    @Override
    public Object execute(Map<String, Object> arguments,
                          ToolExecutionContext context) {
        // 工具逻辑
        return Map.of("success", true);
    }
}
```

### 2. 创建自动配置类

使用 `@AutoConfiguration` 注册工具 Bean：

```java
@AutoConfiguration
@ConditionalOnProperty(prefix = "my.extension", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
public class MyExtensionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MyCustomTool myCustomTool() {
        return new MyCustomTool();
    }
}
```

### 3. 注册自动配置

在 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中添加：

```
com.example.MyExtensionAutoConfiguration
```

### 4. 添加依赖

扩展模块需要依赖 `foggy-mcp-spi`：

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-mcp-spi</artifactId>
    <version>${foggy.version}</version>
</dependency>
```

工具将被 `McpToolDispatcher` 自动发现并注册。

## 下一步

- [工具概述](./overview.md) - 查看所有内置工具
- [查询工具](./query.md) - 了解 query_model 用法
