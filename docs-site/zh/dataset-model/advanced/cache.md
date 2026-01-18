# 查询缓存

Foggy Dataset Model 提供两级缓存机制来加速查询响应：

| 级别 | 名称 | 缓存位置 | 适用场景 |
|------|------|----------|----------|
| L1 | 本地缓存 | JVM 内存 | 高频重复查询 |
| L2 | 分布式缓存 | Redis | 集群部署、跨实例共享 |

## L1 本地缓存

L1 缓存基于 Caffeine 实现，缓存在 JVM 内存中，查询速度最快。

### 配置方式

在 `application.yml` 中配置：

```yaml
foggy:
  dataset:
    cache:
      l1:
        enabled: true
        max-size: 1000        # 最大缓存条目数
        expire-after-write: 5m  # 写入后过期时间
        expire-after-access: 2m # 访问后过期时间（可选）
```

### 配置说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | boolean | true | 是否启用 L1 缓存 |
| `max-size` | int | 1000 | 最大缓存条目数 |
| `expire-after-write` | Duration | 5m | 写入后过期时间 |
| `expire-after-access` | Duration | - | 访问后过期时间（可选） |

### 使用场景

- 单实例部署
- 高频重复查询
- 对实时性要求不高的报表

## L2 分布式缓存

L2 缓存基于 Redis 实现，支持集群部署时的跨实例缓存共享。

### 配置方式

```yaml
foggy:
  dataset:
    cache:
      l2:
        enabled: true
        ttl: 30m              # 缓存过期时间
        key-prefix: "foggy:query:"  # 缓存 key 前缀

spring:
  redis:
    host: localhost
    port: 6379
    password: ""
```

### 配置说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | boolean | false | 是否启用 L2 缓存 |
| `ttl` | Duration | 30m | 缓存过期时间 |
| `key-prefix` | String | "foggy:query:" | Redis key 前缀 |

### 缓存 Key 生成规则

L2 缓存的 key 由以下部分组成：

```
{prefix}{modelName}:{queryHash}
```

其中 `queryHash` 是查询请求的 MD5 哈希值，包含：
- 查询的列（维度、度量）
- 过滤条件
- 排序规则
- 分页参数

## 缓存策略

### 缓存命中流程

```
查询请求
    ↓
检查 L1 缓存 ──命中──→ 返回结果
    │
    未命中
    ↓
检查 L2 缓存 ──命中──→ 写入 L1 → 返回结果
    │
    未命中
    ↓
执行 SQL 查询
    ↓
写入 L1 + L2
    ↓
返回结果
```

### 缓存失效

缓存会在以下情况下失效：

1. **过期失效**：达到配置的 TTL 时间
2. **主动失效**：调用缓存清理 API
3. **容量淘汰**：L1 缓存达到最大条目数时，按 LRU 策略淘汰

### 主动清理缓存

通过 API 清理指定模型的缓存：

```java
@Resource
private QueryCacheManager cacheManager;

// 清理指定模型的所有缓存
cacheManager.evictByModel("FactSalesModel");

// 清理所有缓存
cacheManager.evictAll();
```

## 禁用缓存

### 全局禁用

```yaml
foggy:
  dataset:
    cache:
      l1:
        enabled: false
      l2:
        enabled: false
```

### 单次查询禁用

在查询请求中指定 `noCache` 参数：

```json
{
  "modelName": "FactSalesModel",
  "columns": ["salesDate$month", "salesAmount"],
  "noCache": true
}
```

或在 Java 代码中：

```java
DbQueryRequestDef request = new DbQueryRequestDef();
request.setNoCache(true);
// ... 设置其他参数
```

## 最佳实践

### 1. 合理设置过期时间

- **实时性要求高**：L1 设置 1-5 分钟，L2 设置 5-15 分钟
- **报表类查询**：L1 设置 10-30 分钟，L2 设置 1-2 小时
- **历史数据查询**：可设置更长的过期时间

### 2. 监控缓存命中率

通过 Actuator 端点查看缓存统计：

```
GET /actuator/metrics/cache.gets
GET /actuator/metrics/cache.puts
```

### 3. 预热缓存

对于常用的报表查询，可在应用启动后预热缓存：

```java
@Component
public class CacheWarmer implements ApplicationRunner {
    @Resource
    private JdbcQueryModel salesModel;

    @Override
    public void run(ApplicationArguments args) {
        // 预热常用查询
        salesModel.queryJdbc(buildDailySalesRequest());
        salesModel.queryJdbc(buildMonthlySalesRequest());
    }
}
```

### 4. 结合预聚合使用

缓存与预聚合可以配合使用：

1. 预聚合减少 SQL 计算量
2. 缓存避免重复执行相同查询

两者结合可实现最佳查询性能。

## 常见问题

### Q: L1 和 L2 缓存应该都启用吗？

**A**: 建议根据部署方式选择：
- 单实例：只启用 L1 即可
- 多实例集群：建议同时启用 L1 和 L2

### Q: 缓存数据不一致怎么办？

**A**:
1. 缩短缓存过期时间
2. 在数据变更后主动清理相关缓存
3. 对于实时性要求极高的查询，禁用缓存

### Q: 如何判断缓存是否生效？

**A**: 查看查询结果的 `extData` 中的缓存信息：

```json
{
  "data": [...],
  "extData": {
    "cacheHit": true,
    "cacheLevel": "L1"
  }
}
```
