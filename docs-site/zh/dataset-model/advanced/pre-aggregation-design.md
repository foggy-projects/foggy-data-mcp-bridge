# 预聚合缓存架构设计

> **版本**: 2.0.0
> **状态**: P0 已实现（双层缓存）
> **作者**: Foggy Framework Team

## 1. 概述

### 1.1 背景

在大数据量场景下，聚合查询（如 SUM、COUNT、AVG）的性能成为瓶颈。借鉴 Cube.js 的预聚合机制，我们设计一套可插拔的预聚合缓存方案，在不破坏现有 `foggy-dataset-model` 简洁性的前提下，为有需要的项目提供查询加速能力。

### 1.2 设计原则

| 原则 | 说明 |
|------|------|
| **模块化** | 复杂逻辑放在独立模块，核心模块保持简洁 |
| **可插拔** | 通过 SPI 机制，按需引入预聚合能力 |
| **渐进式** | 分阶段实现：P0 双层缓存 → P1 预聚合表 → P2 增量刷新 |
| **无侵入** | 对现有 QueryFacade 调用方式零改动 |

### 1.3 实施阶段

| 阶段 | 目标 | 存储 | 复杂度 | 状态 |
|------|------|------|--------|------|
| **P0** | 双层查询结果缓存 | Redis / Caffeine | 低 | ✅ 已完成 |
| **P1** | 预聚合表匹配 | DB Table / Collection | 中 | 规划中 |
| **P2** | 增量刷新 + Rollup | DB + Scheduler | 高 | 规划中 |

---

## 2. 双层缓存架构（P0 已实现）

### 2.1 核心设计

双层缓存在不同阶段检查，各有优势：

| 特性 | L1 缓存（Token 级别） | L2 缓存（SQL 级别） |
|------|----------------------|---------------------|
| 缓存键 | authorization + 请求指纹 | 最终 SQL + 参数 |
| 检查时机 | beforeQuery 之后 | SQL 生成之后 |
| 跳过内容 | SQL 构建 + SQL 执行 | 仅 SQL 执行 |
| 默认状态 | **禁用**（需显式启用） | **启用** |
| 权限感知 | 基于 Token（信任 Token） | 基于最终 SQL（精确） |
| 适用场景 | 高频重复查询、相同用户 | 跨用户相同查询 |

### 2.2 核心流程

```
┌──────────────────────────────────────────────────────────────────────┐
│                          查询请求                                     │
└───────────────────────────────┬──────────────────────────────────────┘
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     QueryFacadeImpl.doQuery()                        │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  1. beforeQuery (权限预处理、AutoGroupBy 等)                    │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                ▼                                     │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  2. 【L1 缓存检查】Token 级别                                   │  │
│  │     ├─ 条件: enableL1Cache=true 且 authorization 不为空        │  │
│  │     ├─ 构建 L1 CacheKey: hash(authorization + fingerprint)     │  │
│  │     └─ 命中 → 跳过 SQL 构建和执行，直接返回                    │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                ▼                                     │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  3. QueryModel.query() (JdbcQueryModelImpl / MongoQueryModelImpl)│ │
│  │     │                                                          │  │
│  │     ├─ 3.1 analysisQueryRequest() 注入权限条件                 │  │
│  │     │                                                          │  │
│  │     ├─ 3.2 【L2 缓存检查】SQL 级别                             │  │
│  │     │      ├─ 条件: enableL2Cache=true（默认）                 │  │
│  │     │      ├─ 构建 L2 CacheKey: hash(modelName + SQL + params) │  │
│  │     │      └─ 命中 → 跳过 SQL 执行                             │  │
│  │     │                                                          │  │
│  │     ├─ 3.3 执行查询（仅当 L2 未命中）                          │  │
│  │     │                                                          │  │
│  │     └─ 3.4 【L2 缓存写入】                                     │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                ▼                                     │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  4. 【L1 缓存写入】（仅当 L1 启用且未命中）                     │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                ▼                                     │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  5. process (结果处理)                                          │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

### 2.3 模块结构

```
foggy-dataset-model/           # 核心模块（保持简洁）
├── spi/
│   ├── QueryModel.java
│   ├── TableModel.java
│   ├── QueryCacheProvider.java      # 双层缓存 SPI 接口
│   └── NoOpQueryCacheProvider.java  # 默认空实现
│
foggy-dataset-model-cache/     # 缓存模块
├── fingerprint/
│   ├── QueryFingerprint.java        # 查询指纹（用于 L1 缓存键）
│   └── QueryFingerprintBuilder.java # 指纹构建器
├── provider/
│   ├── RedisQueryCacheProvider.java # Redis 双层缓存实现
│   └── CaffeineQueryCacheProvider.java # Caffeine 双层缓存实现
└── config/
    ├── QueryCacheProperties.java    # 配置属性
    └── QueryCacheAutoConfiguration.java # 自动配置
```

### 2.4 SPI 接口

```java
/**
 * 查询缓存提供者 SPI（双层缓存）
 */
public interface QueryCacheProvider {

    // ==================== Context Keys ====================
    String EXT_ENABLE_L1_CACHE = "enableL1Cache";
    String EXT_ENABLE_L2_CACHE = "enableL2Cache";
    String EXT_AUTHORIZATION = "authorization";
    String EXT_L1_CACHE_HIT = "l1CacheHit";
    String EXT_L2_CACHE_HIT = "l2CacheHit";

    // ==================== L1 缓存：Token 级别 ====================

    /**
     * L1 缓存检查（在 beforeQuery 之后、query 之前）
     * 基于授权令牌和请求参数判断缓存命中。
     * 命中时可完全跳过 SQL 构建和执行。
     */
    default PagingResultImpl checkL1Cache(ModelResultContext context, String authorization) {
        return null;
    }

    /**
     * L1 缓存写入
     */
    default void writeL1Cache(ModelResultContext context, String authorization, PagingResultImpl result) {
        // no-op
    }

    // ==================== L2 缓存：SQL 级别 ====================

    /**
     * L2 缓存检查（在 SQL 生成后、执行前）
     * 基于最终 SQL（包含权限条件）和参数判断缓存命中。
     * 命中时可跳过 SQL 执行，但 SQL 构建已完成。
     */
    default PagingResultImpl checkL2Cache(String modelName, String sql, List<?> params,
                                           ModelResultContext context) {
        return null;
    }

    /**
     * L2 缓存写入
     */
    default void writeL2Cache(String modelName, String sql, List<?> params,
                               PagingResultImpl result, ModelResultContext context) {
        // no-op
    }

    // ==================== 缓存管理 ====================

    /** 清除指定模型的所有缓存（L1 + L2） */
    default void evict(String modelName) {}

    /** 清除所有查询缓存（L1 + L2） */
    default void evictAll() {}

    /** 获取缓存统计信息 */
    default Map<String, Object> getStats() { return Collections.emptyMap(); }

    // ==================== 辅助方法 ====================

    static boolean isL1Enabled(ModelResultContext context) { ... }
    static boolean isL2Enabled(ModelResultContext context) { ... }
    static String getAuthorization(ModelResultContext context) { ... }
}
```

### 2.5 使用方式

**配置**:
```yaml
foggy:
  query-cache:
    enabled: true
    type: redis                    # redis | caffeine
    default-ttl: 5m
    max-result-size: 10000
    model-ttl:
      FactOrders: 10m
      DimCustomer: 1h
    exclude-models:
      - RealtimeDashboard
```

**启用 L1 缓存**:
```java
// L1 需要主动启用
context.getExtData().put(QueryCacheProvider.EXT_ENABLE_L1_CACHE, true);
context.getSecurityContext().setAuthorization(token);
```

**禁用 L2 缓存**:
```java
// L2 默认启用，可显式禁用
context.getExtData().put(QueryCacheProvider.EXT_ENABLE_L2_CACHE, false);
```

**缓存管理**:
```java
@Resource
private QueryCacheProvider queryCacheProvider;

// 清除指定模型的缓存
queryCacheProvider.evict("FactOrders");

// 清除所有缓存
queryCacheProvider.evictAll();

// 查看统计
Map<String, Object> stats = queryCacheProvider.getStats();
// { l1Hits: 100, l1Misses: 20, l2Hits: 500, l2Misses: 80, hitRate: "85.71%" }
```

---

## 3. 不可缓存的查询

以下查询自动跳过缓存：

1. **包含原始 SQL 片段**：权限注入的原始 SQL 条件
2. **包含非确定性函数**：`RAND()`, `NOW()`, `UUID()` 等
3. **结果集过大**：超过 `max-result-size` 配置
4. **排除的模型**：在 `exclude-models` 列表中

---

## 4. 多数据库支持

### 4.1 JDBC（JdbcQueryModelImpl）

L2 缓存键基于：
- 模型名称
- 分页 SQL（包含 LIMIT/OFFSET）
- 参数列表

### 4.2 MongoDB（MongoQueryModelImpl）

L2 缓存键基于：
- 集合名称
- $match 条件
- $addFields 操作
- $project 操作
- $sort 操作
- $skip / $limit

---

## 5. 后续阶段规划

### 5.1 P1: 预聚合表匹配

- 在 TM 文件中定义 `preAggregations`
- 基于 `QueryFingerprint` 匹配预聚合
- 支持 JDBC 和 MongoDB 预聚合表

### 5.2 P2: 增量刷新

- 支持增量刷新策略
- 多层级 Rollup (日 → 周 → 月)
- 定时任务 + 事件驱动刷新

---

## 6. 风险与注意事项

| 风险 | 应对措施 |
|------|----------|
| 数据不一致 | 合理设置 TTL，提供手动清除接口 |
| 缓存击穿 | TTL 加随机偏移（防雪崩） |
| 内存占用 | 限制 maxResultSize，大结果不缓存 |
| Redis 故障 | 降级为无缓存模式，不影响业务 |
| 权限变更 | 清除相关用户的 L1 缓存，L2 缓存通过 TTL 失效 |

---

## 7. 总结

P0 阶段通过双层缓存设计，在不破坏核心模块简洁性的前提下，提供了高效的查询缓存能力：

- **L1 缓存**：Token 级别，可跳过 SQL 构建，适合高频重复查询
- **L2 缓存**：SQL 级别，精确匹配，跨用户复用

用户可以根据项目规模和需求，选择性启用不同层级的缓存。
