# Query Cache

Foggy Dataset Model provides a two-level caching mechanism to accelerate query responses:

| Level | Name | Cache Location | Use Case |
|-------|------|----------------|----------|
| L1 | Local Cache | JVM Memory | High-frequency repeated queries |
| L2 | Distributed Cache | Redis | Cluster deployment, cross-instance sharing |

## L1 Local Cache

L1 cache is implemented based on Caffeine and cached in JVM memory, providing the fastest query speed.

### Configuration

Configure in `application.yml`:

```yaml
foggy:
  dataset:
    cache:
      l1:
        enabled: true
        max-size: 1000        # Maximum number of cache entries
        expire-after-write: 5m  # Expiration time after write
        expire-after-access: 2m # Expiration time after access (optional)
```

### Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | boolean | true | Whether to enable L1 cache |
| `max-size` | int | 1000 | Maximum number of cache entries |
| `expire-after-write` | Duration | 5m | Expiration time after write |
| `expire-after-access` | Duration | - | Expiration time after access (optional) |

### Use Cases

- Single instance deployment
- High-frequency repeated queries
- Reports with low real-time requirements

## L2 Distributed Cache

L2 cache is implemented based on Redis, supporting cross-instance cache sharing during cluster deployment.

### Configuration

```yaml
foggy:
  dataset:
    cache:
      l2:
        enabled: true
        ttl: 30m              # Cache expiration time
        key-prefix: "foggy:query:"  # Cache key prefix

spring:
  redis:
    host: localhost
    port: 6379
    password: ""
```

### Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | boolean | false | Whether to enable L2 cache |
| `ttl` | Duration | 30m | Cache expiration time |
| `key-prefix` | String | "foggy:query:" | Redis key prefix |

### Cache Key Generation Rules

L2 cache key consists of the following parts:

```
{prefix}{modelName}:{queryHash}
```

Where `queryHash` is the MD5 hash value of the query request, including:
- Query columns (dimensions, measures)
- Filter conditions
- Sort rules
- Pagination parameters

## Cache Strategy

### Cache Hit Flow

```
Query Request
    ↓
Check L1 Cache ──Hit──→ Return Result
    │
    Miss
    ↓
Check L2 Cache ──Hit──→ Write to L1 → Return Result
    │
    Miss
    ↓
Execute SQL Query
    ↓
Write to L1 + L2
    ↓
Return Result
```

### Cache Invalidation

Cache will be invalidated in the following situations:

1. **Expiration**: Reaches configured TTL time
2. **Active Invalidation**: Call cache cleanup API
3. **Capacity Eviction**: When L1 cache reaches maximum entries, evicts based on LRU strategy

### Active Cache Cleanup

Clear cache for a specific model through API:

```java
@Resource
private QueryCacheManager cacheManager;

// Clear all cache for a specific model
cacheManager.evictByModel("FactSalesModel");

// Clear all cache
cacheManager.evictAll();
```

## Disabling Cache

### Global Disable

```yaml
foggy:
  dataset:
    cache:
      l1:
        enabled: false
      l2:
        enabled: false
```

### Single Query Disable

Specify `noCache` parameter in query request:

```json
{
  "modelName": "FactSalesModel",
  "columns": ["salesDate$month", "salesAmount"],
  "noCache": true
}
```

Or in Java code:

```java
DbQueryRequestDef request = new DbQueryRequestDef();
request.setNoCache(true);
// ... set other parameters
```

## Best Practices

### 1. Set Reasonable Expiration Time

- **High real-time requirements**: L1 set to 1-5 minutes, L2 set to 5-15 minutes
- **Report queries**: L1 set to 10-30 minutes, L2 set to 1-2 hours
- **Historical data queries**: Can set longer expiration time

### 2. Monitor Cache Hit Rate

View cache statistics through Actuator endpoints:

```
GET /actuator/metrics/cache.gets
GET /actuator/metrics/cache.puts
```

### 3. Warm Up Cache

For frequently used report queries, warm up cache after application startup:

```java
@Component
public class CacheWarmer implements ApplicationRunner {
    @Resource
    private JdbcQueryModel salesModel;

    @Override
    public void run(ApplicationArguments args) {
        // Warm up common queries
        salesModel.queryJdbc(buildDailySalesRequest());
        salesModel.queryJdbc(buildMonthlySalesRequest());
    }
}
```

### 4. Use with Pre-Aggregation

Cache and pre-aggregation can work together:

1. Pre-aggregation reduces SQL computation
2. Cache avoids repeated execution of the same query

Combining both can achieve optimal query performance.

## Common Questions

### Q: Should both L1 and L2 cache be enabled?

**A**: Recommended based on deployment mode:
- Single instance: Enable L1 only
- Multi-instance cluster: Enable both L1 and L2

### Q: What to do if cache data is inconsistent?

**A**:
1. Shorten cache expiration time
2. Actively clear relevant cache after data changes
3. For queries with extremely high real-time requirements, disable cache

### Q: How to determine if cache is effective?

**A**: Check cache information in query result's `extData`:

```json
{
  "data": [...],
  "extData": {
    "cacheHit": true,
    "cacheLevel": "L1"
  }
}
```
