# 预聚合与缓存技术参考

## 预聚合核心类

### 定义层 (def/preagg/)

```
PreAggregationDef      - TM 文件中的预聚合定义
├── name               - 预聚合名称
├── tableName          - 预聚合表名
├── priority           - 优先级 (1-100)
├── dimensions         - 维度列表
├── granularity        - 时间粒度 {dimName: 'day'|'month'|...}
├── dimensionProperties- 维度属性 {dimName: ['prop1', 'prop2']}
├── measures           - 度量列表 [{name, aggregation}]
├── refresh            - 刷新配置
└── filters            - 永久过滤条件
```

### 运行时层 (spi/preagg/)

```
PreAggregation         - 运行时预聚合接口
├── getDimensionNames()      - 获取维度集合
├── getGranularities()       - 获取粒度配置
├── getDimensionProperties() - 获取属性配置
├── getMeasureAggregations() - 获取度量聚合方式
├── hasDimension(name)       - 检查维度
├── hasMeasure(name)         - 检查度量
├── isDataStale()            - 检查数据是否过期
└── supportsHybridQuery()    - 是否支持混合查询
```

### 匹配层 (engine/preagg/)

```
PreAggQueryRequirement       - 查询需求（从 JdbcQuery 提取）
├── dimensionNames           - 查询的维度
├── dimensionProperties      - 查询的属性
├── measureAggregations      - 查询的度量
├── queryGranularities       - 查询的粒度
└── isSatisfiableBy(preAgg)  - 检查是否满足

PreAggQueryRequirementBuilder - 需求构建器
├── build(request, jdbcQuery, model) - 构建需求
├── processColumn()          - 处理列
├── processDimensionColumn() - 处理维度列
├── processPropertyColumn()  - 处理属性列（含时间粒度检测）
└── processMeasureColumn()   - 处理度量列

PreAggregationMatcher        - 匹配器
├── findBestMatch()          - 找最佳预聚合
└── calculateScore()         - 计算匹配分数

PreAggQueryRewriter          - 查询重写器
├── rewrite()                - 重写查询
├── buildDirectQuery()       - 直接查询
├── buildRollupQuery()       - Rollup 查询
└── buildHybridQuery()       - 混合查询（UNION）
```

## 时间粒度体系

### TimeGranularity 枚举

```java
public enum TimeGranularity {
    MINUTE(1),      // 分钟
    HOUR(60),       // 小时
    DAY(1440),      // 天
    WEEK(10080),    // 周
    MONTH(43200),   // 月
    QUARTER(129600),// 季度
    YEAR(525600);   // 年

    // 判断能否向上聚合
    public boolean canRollupTo(TimeGranularity target) {
        return this.level <= target.level;
    }
}
```

### 时间属性到粒度映射

```java
// PreAggQueryRequirementBuilder 中的映射
Map<String, TimeGranularity> TIME_PROPERTY_GRANULARITY = Map.of(
    "year", TimeGranularity.YEAR,
    "quarter", TimeGranularity.QUARTER,
    "month", TimeGranularity.MONTH,
    "week", TimeGranularity.WEEK,
    "day", TimeGranularity.DAY,
    "hour", TimeGranularity.HOUR,
    "minute", TimeGranularity.MINUTE
);
```

## 属性名规范化

### 规范化规则

```java
// camelCase → snake_case
private static String normalizePropertyName(String name) {
    if (name.contains("_")) {
        return name.toLowerCase();  // 已是 snake_case
    }
    // categoryName → category_name
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
        char c = name.charAt(i);
        if (Character.isUpperCase(c)) {
            if (i > 0) result.append('_');
            result.append(Character.toLowerCase(c));
        } else {
            result.append(c);
        }
    }
    return result.toString();
}
```

### 隐式属性

以下属性无需在预聚合中显式配置：
- `caption` - 维度显示列
- `id` - 维度主键

### 时间粒度属性

以下属性通过粒度配置匹配，不作为普通属性检查：
- `year`, `quarter`, `month`, `week`, `day`, `hour`, `minute`

## 聚合兼容性矩阵

```java
private boolean isAggregationCompatible(DbAggregation preAggAgg, DbAggregation queryAgg) {
    if (preAggAgg == queryAgg) return true;

    // SUM → SUM
    if (preAggAgg == SUM && queryAgg == SUM) return true;

    // COUNT → SUM (rollup 时)
    if (preAggAgg == COUNT && queryAgg == SUM) return true;

    // MIN → MIN
    if (preAggAgg == MIN && queryAgg == MIN) return true;

    // MAX → MAX
    if (preAggAgg == MAX && queryAgg == MAX) return true;

    // AVG 需要 SUM + COUNT
    return false;
}
```

## 匹配分数计算

```java
private int calculateScore(PreAggregation p, PreAggQueryRequirement req) {
    int score = p.getPriority() * 100;  // priority 权重最高
    score -= (p.getDimensionCount() - req.getDimensionCount()) * 10;  // 维度数接近
    score -= (p.getGranularityLevel() - req.getGranularityLevel());   // 粒度接近
    return score;
}
```

## 混合查询（Hybrid Query）

### 触发条件

```java
// PreAggregation.isDataStale()
default boolean isDataStale() {
    Object watermark = getDataWatermark();
    if (watermark != null) {
        LocalDate today = LocalDate.now();
        if (watermark instanceof LocalDate watermarkDate) {
            return watermarkDate.isBefore(today);
        }
        if (watermark instanceof LocalDateTime watermarkDateTime) {
            return watermarkDateTime.toLocalDate().isBefore(today);
        }
    }
    // 无 watermark，检查 lastRefreshTime
    LocalDateTime lastRefresh = getLastRefreshTime();
    if (lastRefresh == null) return true;
    return lastRefresh.plusHours(24).isBefore(LocalDateTime.now());
}
```

### SQL 结构

```sql
-- 混合查询 SQL 结构
SELECT ... FROM (
    -- 预聚合数据（watermark 之前）
    SELECT ... FROM preagg_table WHERE date_col < watermark
    UNION ALL
    -- 原始表数据（watermark 之后）
    SELECT ... FROM fact_table WHERE date_col >= watermark
) t
GROUP BY ...
```

## 缓存配置

### QueryCacheConfig 字段

```java
public static class QueryCacheConfig {
    boolean l1Enabled = false;      // L1 缓存开关
    boolean l2Enabled = true;       // L2 缓存开关
    boolean l1CacheHit;             // L1 命中标记
    boolean l2CacheHit;             // L2 命中标记
    QueryCacheProvider provider;    // 缓存提供者
}
```

### 缓存流程

```
请求 → L1检查 → 命中 → 返回
           ↓
         未命中
           ↓
       L2检查 → 命中 → 写入L1 → 返回
           ↓
         未命中
           ↓
       执行SQL → 写入L1+L2 → 返回
```

## 调试技巧

### 1. 开启详细日志

```yaml
logging:
  level:
    com.foggyframework.dataset.db.model.engine.preagg: DEBUG
    com.foggyframework.dataset.db.model.engine.query_model: DEBUG
```

### 2. 查看匹配过程

`PreAggQueryRequirementBuilder.build()` 会输出：
```
JdbcQuery has X columns in select
Column[0]: name=..., isDimension=..., isMeasure=...
Built query requirement: PreAggQueryRequirement{dimensions=..., properties=..., measures=...}
```

### 3. 检查查询结果

```java
DbQueryResult result = model.query(...);
Map<String, Object> extData = result.getPagingResult().getExtData();

String preAggUsed = (String) extData.get("preAggUsed");
String preAggMode = (String) extData.get("preAggMode");
Boolean cacheHit = (Boolean) extData.get("cacheHit");
```

### 4. 单步调试关键断点

| 功能 | 断点位置 |
|------|----------|
| 需求提取 | `PreAggQueryRequirementBuilder.processColumn()` |
| 匹配检查 | `PreAggQueryRequirement.isSatisfiableBy()` |
| 属性匹配 | 循环 `dimensionProperties` 的位置 |
| 粒度匹配 | `TimeGranularity.canRollupTo()` |
| SQL重写 | `PreAggQueryRewriter.rewrite()` |
