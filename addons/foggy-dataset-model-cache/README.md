# foggy-dataset-model-cache

查询结果双层缓存模块，为 Foggy Dataset Model 提供可插拔的查询缓存能力。

## 特性

- **双层缓存架构**：
  - **L1 缓存（Token 级别）**：基于授权令牌 + 请求指纹，可跳过 SQL 构建
  - **L2 缓存（SQL 级别）**：基于最终 SQL/Pipeline + 参数，精确匹配
- **可插拔设计**：通过 SPI 机制集成，不引入即不影响核心模块
- **多缓存后端**：支持 Redis（分布式）和 Caffeine（本地）
- **智能指纹**：基于完整 JdbcQuery/MongoDB Pipeline 计算缓存键
- **安全缓存**：原始 SQL 片段的查询自动跳过缓存
- **灵活配置**：按模型配置 TTL、排除列表、结果大小限制

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-model-cache</artifactId>
    <version>${foggy.version}</version>
</dependency>
```

### 2. 配置

```yaml
foggy:
  query-cache:
    enabled: true
    type: redis                    # redis | caffeine
    default-ttl: 5m                # 默认 TTL
    max-result-size: 10000         # 超过此数量不缓存
    model-ttl:
      FactOrders: 10m              # 按模型配置 TTL
      DimCustomer: 1h
    exclude-models:
      - RealtimeDashboard          # 排除的模型
```

### 3. 使用

引入依赖并配置后，L2 缓存自动生效，无需修改业务代码。

#### 启用 L1 缓存（Token 级别）

L1 缓存需要主动启用，通过在请求上下文中设置标记：

```java
// 在 ModelResultContext 中启用 L1 缓存
context.getExtData().put(QueryCacheProvider.EXT_ENABLE_L1_CACHE, true);
context.getExtData().put(QueryCacheProvider.EXT_AUTHORIZATION, authorization);
```

或通过 SecurityContext 注入 authorization：

```java
context.getSecurityContext().setAuthorization(token);
context.getExtData().put(QueryCacheProvider.EXT_ENABLE_L1_CACHE, true);
```

#### 禁用 L2 缓存

L2 缓存默认启用，可通过标记禁用：

```java
// 禁用 L2 缓存
context.getExtData().put(QueryCacheProvider.EXT_ENABLE_L2_CACHE, false);
```

## 缓存管理

### 清除缓存

```java
@Resource
private QueryCacheProvider queryCacheProvider;

// 清除单个模型的缓存（L1 + L2）
queryCacheProvider.evict("FactOrders");

// 清除所有缓存（L1 + L2）
queryCacheProvider.evictAll();
```

### 查看统计

```java
Map<String, Object> stats = queryCacheProvider.getStats();
// {
//   type: "redis",
//   enabled: true,
//   l1Hits: 100,
//   l1Misses: 20,
//   l2Hits: 500,
//   l2Misses: 80,
//   hitRate: "85.71%",
//   defaultTtl: "PT5M"
// }
```

## 双层缓存架构

```
QueryFacadeImpl.doQuery()
    │
    ├─ 1. beforeQuery (权限预处理)
    │
    ├─ 2. 【L1 缓存检查】Token 级别
    │      ├─ 条件: enableL1Cache=true 且 authorization 不为空
    │      ├─ 构建 L1 CacheKey: hash(authorization + fingerprint)
    │      └─ 命中 → 跳过 SQL 构建和执行
    │
    ├─ 3. QueryModel.query() (JdbcQueryModelImpl / MongoQueryModelImpl)
    │      │
    │      ├─ 3.1 analysisQueryRequest() 注入权限条件
    │      │
    │      ├─ 3.2 【L2 缓存检查】SQL 级别
    │      │      ├─ 条件: enableL2Cache=true（默认）
    │      │      ├─ 构建 L2 CacheKey: hash(modelName + SQL + params)
    │      │      └─ 命中 → 跳过 SQL 执行
    │      │
    │      ├─ 3.3 执行查询（仅当 L2 未命中）
    │      │
    │      └─ 3.4 【L2 缓存写入】
    │
    ├─ 4. 【L1 缓存写入】（仅当 L1 启用且未命中）
    │
    └─ 5. process (结果处理)
```

### L1 vs L2 缓存对比

| 特性 | L1 缓存（Token 级别） | L2 缓存（SQL 级别） |
|------|----------------------|---------------------|
| 缓存键 | authorization + 请求指纹 | 最终 SQL + 参数 |
| 检查时机 | beforeQuery 之后 | SQL 生成之后 |
| 跳过内容 | SQL 构建 + SQL 执行 | 仅 SQL 执行 |
| 默认状态 | 禁用（需显式启用） | 启用 |
| 权限感知 | 基于 Token（信任 Token） | 基于最终 SQL（精确） |
| 适用场景 | 高频重复查询、相同用户 | 跨用户相同查询 |

## 不可缓存的查询

以下查询会自动跳过缓存：

1. **包含原始 SQL 片段**：权限注入的原始 SQL 条件
2. **包含非确定性函数**：`RAND()`, `NOW()`, `UUID()` 等
3. **结果集过大**：超过 `max-result-size` 配置
4. **排除的模型**：在 `exclude-models` 列表中

## 模块依赖

```
foggy-dataset-model (核心)
    └── spi/QueryCacheProvider.java (SPI 接口)
    └── spi/NoOpQueryCacheProvider.java (默认空实现)

foggy-dataset-model-cache (本模块)
    ├── fingerprint/ (查询指纹)
    ├── provider/    (缓存实现)
    │   ├── RedisQueryCacheProvider.java
    │   └── CaffeineQueryCacheProvider.java
    └── config/      (自动配置)
```

## License

Apache License 2.0
