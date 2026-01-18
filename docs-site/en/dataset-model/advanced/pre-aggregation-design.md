# Pre-Aggregation Cache Architecture Design

> **Version**: 2.0.0
> **Status**: P0 Implemented (Two-level cache)
> **Author**: Foggy Framework Team

## 1. Overview

### 1.1 Background

In large-scale data scenarios, aggregation query performance (such as SUM, COUNT, AVG) becomes a bottleneck. Drawing from Cube.js's pre-aggregation mechanism, we design a pluggable pre-aggregation cache solution that provides query acceleration capabilities for projects that need it without compromising the simplicity of the existing `foggy-dataset-model`.

### 1.2 Design Principles

| Principle | Description |
|-----------|-------------|
| **Modular** | Complex logic in independent modules, core modules remain simple |
| **Pluggable** | Introduce pre-aggregation capabilities on-demand through SPI mechanism |
| **Progressive** | Phased implementation: P0 two-level cache → P1 pre-aggregation table → P2 incremental refresh |
| **Non-invasive** | Zero changes to existing QueryFacade call methods |

### 1.3 Implementation Phases

| Phase | Goal | Storage | Complexity | Status |
|-------|------|---------|------------|--------|
| **P0** | Two-level query result cache | Redis / Caffeine | Low | ✅ Completed |
| **P1** | Pre-aggregation table matching | DB Table / Collection | Medium | Planned |
| **P2** | Incremental refresh + Rollup | DB + Scheduler | High | Planned |

---

## 2. Two-Level Cache Architecture (P0 Implemented)

### 2.1 Core Design

Two-level cache checks at different stages, each with advantages:

| Feature | L1 Cache (Token Level) | L2 Cache (SQL Level) |
|---------|------------------------|----------------------|
| Cache Key | authorization + request fingerprint | Final SQL + parameters |
| Check Timing | After beforeQuery | After SQL generation |
| Skip Content | SQL construction + SQL execution | Only SQL execution |
| Default State | **Disabled** (requires explicit enable) | **Enabled** |
| Permission Aware | Based on Token (trust Token) | Based on final SQL (precise) |
| Use Case | High-frequency repeated queries, same user | Cross-user same queries |

### 2.2 Core Flow

```
┌──────────────────────────────────────────────────────────────────────┐
│                          Query Request                                │
└───────────────────────────────┬──────────────────────────────────────┘
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     QueryFacadeImpl.doQuery()                        │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  1. beforeQuery (permission preprocessing, AutoGroupBy, etc.)   │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                ▼                                     │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  2. 【L1 Cache Check】Token Level                              │  │
│  │     ├─ Condition: enableL1Cache=true and authorization not empty│  │
│  │     ├─ Build L1 CacheKey: hash(authorization + fingerprint)   │  │
│  │     └─ Hit → Skip SQL construction and execution, return directly│  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                ▼                                     │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  3. QueryModel.query() (JdbcQueryModelImpl / MongoQueryModelImpl)│ │
│  │     │                                                          │  │
│  │     ├─ 3.1 analysisQueryRequest() Inject permission conditions │  │
│  │     │                                                          │  │
│  │     ├─ 3.2 【L2 Cache Check】SQL Level                         │  │
│  │     │      ├─ Condition: enableL2Cache=true (default)         │  │
│  │     │      ├─ Build L2 CacheKey: hash(modelName + SQL + params)│  │
│  │     │      └─ Hit → Skip SQL execution                        │  │
│  │     │                                                          │  │
│  │     ├─ 3.3 Execute query (only when L2 miss)                  │  │
│  │     │                                                          │  │
│  │     └─ 3.4 【L2 Cache Write】                                  │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                ▼                                     │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  4. 【L1 Cache Write】(only when L1 enabled and miss)          │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                ▼                                     │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  5. process (result processing)                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

### 2.3 Module Structure

```
foggy-dataset-model/           # Core module (keep simple)
├── spi/
│   ├── QueryModel.java
│   ├── TableModel.java
│   ├── QueryCacheProvider.java      # Two-level cache SPI interface
│   └── NoOpQueryCacheProvider.java  # Default no-op implementation
│
foggy-dataset-model-cache/     # Cache module
├── fingerprint/
│   ├── QueryFingerprint.java        # Query fingerprint (for L1 cache key)
│   └── QueryFingerprintBuilder.java # Fingerprint builder
├── provider/
│   ├── RedisQueryCacheProvider.java # Redis two-level cache implementation
│   └── CaffeineQueryCacheProvider.java # Caffeine two-level cache implementation
├── controller/
│   └── QueryCacheController.java    # REST API controller
├── eviction/
│   ├── EvictQueryCache.java         # Cache auto-invalidation annotation
│   └── CacheEvictionAspect.java     # Cache invalidation AOP aspect
└── config/
    ├── QueryCacheProperties.java    # Configuration properties
    └── QueryCacheAutoConfiguration.java # Auto-configuration
```

### 2.4 Cache Configuration Class

Cache configuration is managed through `ModelResultContext.QueryCacheConfig` class, providing type-safe configuration:

```java
/**
 * Query cache configuration (inner class of ModelResultContext)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class QueryCacheConfig {
    @Builder.Default
    private boolean l1Enabled = false;    // L1 cache (Token level), default disabled
    @Builder.Default
    private boolean l2Enabled = true;     // L2 cache (SQL level), default enabled
    private boolean l1CacheHit;           // L1 cache hit status
    private boolean l2CacheHit;           // L2 cache hit status

    /** Enable L1 cache */
    public static QueryCacheConfig enableL1() {
        return QueryCacheConfig.builder().l1Enabled(true).l2Enabled(true).build();
    }

    /** Disable all cache */
    public static QueryCacheConfig disabled() {
        return QueryCacheConfig.builder().l1Enabled(false).l2Enabled(false).build();
    }

    /** Default configuration (only L2 enabled) */
    public static QueryCacheConfig defaultConfig() {
        return QueryCacheConfig.builder().l1Enabled(false).l2Enabled(true).build();
    }
}
```

### 2.5 SPI Interface

```java
/**
 * Query cache provider SPI (two-level cache)
 */
public interface QueryCacheProvider {

    // ==================== L1 Cache: Token Level ====================

    /**
     * L1 cache check (after beforeQuery, before query)
     */
    default PagingResultImpl checkL1Cache(ModelResultContext context, String authorization) {
        return null;
    }

    /**
     * L1 cache write
     */
    default void writeL1Cache(ModelResultContext context, String authorization, PagingResultImpl result) {
        // no-op
    }

    // ==================== L2 Cache: SQL Level ====================

    /**
     * L2 cache check (after SQL generation, before execution)
     */
    default PagingResultImpl checkL2Cache(String modelName, String sql, List<?> params,
                                           ModelResultContext context) {
        return null;
    }

    /**
     * L2 cache write
     */
    default void writeL2Cache(String modelName, String sql, List<?> params,
                               PagingResultImpl result, ModelResultContext context) {
        // no-op
    }

    // ==================== Cache Management ====================

    /** Clear all cache for specified model (L1 + L2) */
    default void evict(String modelName) {}

    /** Clear all query cache (L1 + L2) */
    default void evictAll() {}

    /** Get cache statistics */
    default Map<String, Object> getStats() { return Collections.emptyMap(); }

    // ==================== Helper Methods ====================

    static boolean isL1Enabled(ModelResultContext context) {
        if (context == null) return false;
        QueryCacheConfig config = context.getCacheConfig();
        return config != null && config.isL1Enabled();
    }

    static boolean isL2Enabled(ModelResultContext context) {
        if (context == null) return true;
        QueryCacheConfig config = context.getCacheConfig();
        return config == null || config.isL2Enabled();
    }

    static void markL1Hit(ModelResultContext context) {
        if (context != null) {
            getOrCreateConfig(context).setL1CacheHit(true);
        }
    }

    static void markL2Hit(ModelResultContext context) {
        if (context != null) {
            getOrCreateConfig(context).setL2CacheHit(true);
        }
    }

    static QueryCacheConfig getOrCreateConfig(ModelResultContext context) {
        QueryCacheConfig config = context.getCacheConfig();
        if (config == null) {
            config = QueryCacheConfig.defaultConfig();
            context.setCacheConfig(config);
        }
        return config;
    }
}
```

### 2.6 Usage

**Configuration**:
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
    api:
      enabled: true                # Enable REST API (default true)
    eviction:
      enabled: true                # Enable auto-invalidation aspect (default true)
```

**Enable L1 Cache** (new API):
```java
// Method 1: Use QueryCacheConfig.enableL1()
context.setCacheConfig(ModelResultContext.QueryCacheConfig.enableL1());
context.getSecurityContext().setAuthorization(token);

// Method 2: Use Builder
context.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
    .l1Enabled(true)
    .l2Enabled(true)
    .build());
```

**Disable L2 Cache**:
```java
// Method 1: Disable all cache
context.setCacheConfig(ModelResultContext.QueryCacheConfig.disabled());

// Method 2: Disable only L2
context.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
    .l1Enabled(false)
    .l2Enabled(false)
    .build());
```

**Check Cache Hit**:
```java
// Check cache hit status after query
QueryCacheConfig config = context.getCacheConfig();
if (config != null) {
    boolean l1Hit = config.isL1CacheHit();
    boolean l2Hit = config.isL2CacheHit();
    log.info("Cache status: L1={}, L2={}", l1Hit ? "HIT" : "MISS", l2Hit ? "HIT" : "MISS");
}
```

**Cache Management**:
```java
@Resource
private QueryCacheProvider queryCacheProvider;

// Clear cache for specified model
queryCacheProvider.evict("FactOrders");

// Clear all cache
queryCacheProvider.evictAll();

// View statistics
Map<String, Object> stats = queryCacheProvider.getStats();
// { l1Hits: 100, l1Misses: 20, l2Hits: 500, l2Misses: 80, hitRate: "85.71%" }
```

### 2.7 Cache Management REST API

Cache module provides REST API for cache management:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/query-cache/stats` | GET | Get cache configuration and statistics |
| `/api/query-cache/evict/{modelName}` | DELETE | Clear cache for specified model |
| `/api/query-cache/evict-all` | DELETE | Clear all cache |
| `/api/query-cache/evict-batch` | POST | Batch clear cache for specified models |

**Request Examples**:

```bash
# Get cache statistics
curl -X GET http://localhost:8080/api/query-cache/stats

# Clear cache for specified model
curl -X DELETE http://localhost:8080/api/query-cache/evict/FactOrders

# Clear all cache
curl -X DELETE http://localhost:8080/api/query-cache/evict-all

# Batch clear
curl -X POST http://localhost:8080/api/query-cache/evict-batch \
  -H "Content-Type: application/json" \
  -d '{"models": ["FactOrders", "DimCustomer"]}'
```

**Response Examples**:

```json
// GET /api/query-cache/stats
{
  "config": {
    "enabled": true,
    "type": "redis",
    "defaultTtl": "PT5M",
    "maxResultSize": 10000,
    "cacheEmptyResult": true,
    "excludeModels": ["RealtimeDashboard"]
  },
  "stats": {
    "l1Hits": 100,
    "l1Misses": 20,
    "l2Hits": 500,
    "l2Misses": 80,
    "hitRate": "85.71%"
  }
}
```

### 2.8 Data Change Auto-Invalidation

Use `@EvictQueryCache` annotation to automatically clear related cache after data changes:

**Annotation Definition**:
```java
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EvictQueryCache {
    /** List of model names to clear cache */
    String[] models() default {};

    /** SpEL expression to dynamically get model name */
    String modelsExpression() default "";

    /** Whether to clear all cache */
    boolean evictAll() default false;

    /** Whether to clear cache before method execution (default after method success) */
    boolean beforeInvocation() default false;

    /** Whether to clear cache only when method has no exception (default true) */
    boolean onlyOnSuccess() default true;
}
```

**Usage Examples**:

```java
// Clear cache for single model
@EvictQueryCache(models = "FactOrders")
public void saveOrder(Order order) {
    orderRepository.save(order);
}

// Clear cache for multiple models
@EvictQueryCache(models = {"FactOrders", "DimCustomer"})
public void updateOrderWithCustomer(Order order) {
    orderRepository.save(order);
    customerRepository.save(order.getCustomer());
}

// Clear all cache (for batch import scenarios)
@EvictQueryCache(evictAll = true)
public void importData(List<Order> orders) {
    orderRepository.saveAll(orders);
}

// Use SpEL expression to dynamically get model name
@EvictQueryCache(modelsExpression = "#order.modelName")
public void saveByModel(Order order) {
    dynamicRepository.save(order);
}

// Use SpEL to access method return value
@EvictQueryCache(modelsExpression = "#result.affectedModels")
public SaveResult saveWithAffectedModels(Order order) {
    return orderRepository.saveAndReturnAffected(order);
}

// Clear cache even on exception
@EvictQueryCache(models = "FactOrders", onlyOnSuccess = false)
public void updateOrderWithRollback(Order order) {
    orderRepository.update(order);
}

// Clear cache before method execution (for scenarios requiring immediate invalidation)
@EvictQueryCache(models = "FactOrders", beforeInvocation = true)
public void deleteOrder(Long orderId) {
    orderRepository.deleteById(orderId);
}
```

**SpEL Expression Support**:
- `#parameterName` - Access method parameters
- `#result` - Access method return value
- `#root.methodName` - Method name

Expression should return `String` or `Collection<String>`.

---

## 3. Non-Cacheable Queries

The following queries automatically skip cache:

1. **Contains raw SQL fragments**: Permission-injected raw SQL conditions
2. **Contains non-deterministic functions**: `RAND()`, `NOW()`, `UUID()`, etc.
3. **Result set too large**: Exceeds `max-result-size` configuration
4. **Excluded models**: In `exclude-models` list

---

## 4. Multi-Database Support

### 4.1 JDBC (JdbcQueryModelImpl)

L2 cache key based on:
- Model name
- Paginated SQL (including LIMIT/OFFSET)
- Parameter list

### 4.2 MongoDB (MongoQueryModelImpl)

L2 cache key based on:
- Collection name
- $match conditions
- $addFields operations
- $project operations
- $sort operations
- $skip / $limit

---

## 5. Future Phase Planning

### 5.1 P1: Pre-Aggregation Table Matching

- Define `preAggregations` in TM file
- Match pre-aggregation based on `QueryFingerprint`
- Support JDBC and MongoDB pre-aggregation tables

### 5.2 P2: Incremental Refresh

- Support incremental refresh strategy
- Multi-level Rollup (day → week → month)
- Scheduled tasks + event-driven refresh

---

## 6. Risks and Considerations

| Risk | Mitigation |
|------|------------|
| Data inconsistency | Set reasonable TTL, use `@EvictQueryCache` for auto-invalidation, or manual clear via REST API |
| Cache avalanche | TTL with random offset |
| Memory usage | Limit maxResultSize, don't cache large results |
| Redis failure | Degrade to no-cache mode, doesn't affect business |
| Permission changes | Clear L1 cache for affected users, L2 cache expires via TTL |

---

## 7. Summary

The P0 phase provides efficient query caching capabilities through a two-level cache design without compromising the simplicity of the core module:

- **L1 Cache**: Token level, can skip SQL construction, suitable for high-frequency repeated queries
- **L2 Cache**: SQL level, precise matching, cross-user reuse

Users can selectively enable different levels of cache based on project scale and requirements.
