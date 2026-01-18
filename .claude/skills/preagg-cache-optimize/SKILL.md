---
name: preagg-cache-optimize
description: 预聚合与缓存优化技能。当用户需要调试预聚合匹配问题、优化查询性能、配置缓存策略、或排查缓存命中问题时使用。
---

# 预聚合与缓存优化

诊断和优化 Foggy Dataset Model 的预聚合匹配与查询缓存。

## 使用场景

当用户需要以下操作时使用：
- 调试预聚合为什么没有被命中
- 优化预聚合配置提升查询性能
- 配置 L1/L2 缓存策略
- 排查缓存命中率低的问题

## 核心知识

### 预聚合匹配规则

匹配必须同时满足以下条件：

| 条件 | 规则 |
|------|------|
| 维度 | 查询维度 ⊆ 预聚合维度 |
| 属性 | 查询属性 ⊆ 预聚合属性（caption/id 为隐式属性） |
| 粒度 | 查询粒度 ≥ 预聚合粒度（DAY→MONTH 可以，MONTH→DAY 不行） |
| 度量 | 查询度量 ⊆ 预聚合度量 |
| 聚合 | 聚合方式兼容（SUM→SUM, COUNT→SUM, MIN→MIN, MAX→MAX） |

### 属性名规范化

TM 文件使用 snake_case，查询可能使用 camelCase：
- `category_name` ↔ `categoryName` 会自动规范化匹配
- 时间粒度属性（year, month, day 等）通过粒度配置匹配，不作为普通属性

### 缓存层级

| 级别 | 位置 | Key 组成 | 命中条件 |
|------|------|----------|----------|
| L1 | JVM 内存 | authorization + 请求指纹 | 同一用户相同请求 |
| L2 | Redis | SQL + 参数 hash | 相同 SQL（含权限条件） |

## 执行流程

### 场景一：预聚合未命中诊断

1. 读取相关 TM 文件，获取 `preAggregations` 配置
2. 分析用户的查询请求，提取：
   - 查询的维度列表
   - 查询的维度属性（从列名解析，如 `product$categoryName`）
   - 查询的度量及聚合方式
   - 时间粒度（从属性名推断，如 `salesDate$month` → MONTH）
3. 逐条检查匹配规则，定位不满足的条件
4. 输出诊断结果和修复建议

### 场景二：预聚合配置优化

1. 分析现有预聚合配置
2. 检查以下问题：
   - 缺少常用维度组合
   - 粒度设置过细导致存储浪费
   - 优先级设置不合理
   - 缺少必要的维度属性
3. 输出优化建议

### 场景三：缓存配置调优

1. 确认当前缓存配置（application.yml）
2. 分析使用场景：
   - 单实例 vs 集群部署
   - 实时性要求
   - 查询模式（高频重复 vs 多样化）
3. 输出配置建议

## 关键代码位置

| 功能 | 文件路径 |
|------|----------|
| 预聚合定义解析 | `def/preagg/PreAggregationDef.java` |
| 查询需求提取 | `engine/preagg/PreAggQueryRequirementBuilder.java` |
| 匹配逻辑 | `engine/preagg/PreAggQueryRequirement.java#isSatisfiableBy()` |
| 查询重写 | `engine/preagg/PreAggQueryRewriter.java` |
| 缓存配置 | `plugins/result_set_filter/ModelResultContext.QueryCacheConfig` |
| 缓存接口 | `spi/QueryCacheProvider.java` |

## 常见问题诊断

### 问题：属性匹配失败

检查点：
1. TM 中 `dimensionProperties` 是否配置了该属性
2. 属性名大小写是否一致（会自动规范化 camelCase ↔ snake_case）
3. 是否是时间粒度属性（year/month/day 等需要通过 granularity 配置）

修复示例：
```javascript
// 查询使用 product$categoryName
// TM 需要配置：
dimensionProperties: {
    product: ['category_name']  // snake_case 格式
}
```

### 问题：时间粒度不匹配

检查点：
1. 查询的时间属性（如 `salesDate$month`）对应的粒度
2. 预聚合的 granularity 配置

规则：预聚合粒度必须 ≤ 查询粒度
- 预聚合 DAY，查询 MONTH → 可匹配（DAY 可 rollup 到 MONTH）
- 预聚合 MONTH，查询 DAY → 不可匹配

### 问题：度量聚合不兼容

兼容矩阵：
| 预聚合 | 查询 | 兼容 |
|--------|------|------|
| SUM | SUM | 是 |
| COUNT | SUM | 是（rollup 时 COUNT 变 SUM） |
| MIN | MIN | 是 |
| MAX | MAX | 是 |
| AVG | - | 需要 SUM+COUNT 组合 |

### 问题：混合查询（Hybrid）触发条件

触发条件：
1. 预聚合配置了 `watermarkColumn`
2. `dataWatermark` 在今天之前

检查 `PreAggregation.isDataStale()` 返回值。

## 调试命令

```java
// 开启预聚合匹配日志
logging.level.com.foggyframework.dataset.db.model.engine.preagg=DEBUG

// 查看匹配结果
// 在查询结果 extData 中：
{
  "preAggUsed": "daily_product_sales",  // 使用的预聚合名称
  "preAggMode": "direct|rollup|hybrid"  // 匹配模式
}
```

## 输出格式

### 诊断报告

```
## 预聚合匹配诊断

**查询需求**：
- 维度: [salesDate, product]
- 属性: {product: [categoryName]}
- 度量: {salesAmount: SUM}
- 粒度: {salesDate: MONTH}

**检查结果**：

| 预聚合 | 维度 | 属性 | 粒度 | 度量 | 结果 |
|--------|------|------|------|------|------|
| daily_product_sales | ✓ | ✗ | ✓ | ✓ | 不匹配 |

**不匹配原因**：
- `daily_product_sales`: 缺少属性 `category_name`

**修复建议**：
在 TM 文件的预聚合配置中添加：
\`\`\`javascript
dimensionProperties: {
    product: ['category_name']
}
\`\`\`
```

## 约束条件

- 不直接修改 TM 文件，仅提供修改建议
- 诊断时列出所有预聚合的匹配情况，不仅是第一个失败的
- 缓存配置建议需考虑部署环境（单实例/集群）

## 决策规则

- 如果用户提供查询请求 JSON → 解析并提取查询需求
- 如果用户只说"预聚合没生效" → 先询问具体的查询和 TM 文件
- 如果涉及 L2 缓存问题 → 检查 Redis 连接配置
- 如果涉及混合查询 → 重点检查 watermark 配置和 isDataStale() 逻辑
