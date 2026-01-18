# 预聚合

预聚合（Pre-Aggregation）是一种通过预先计算聚合结果来加速查询的技术。系统会在查询时自动选择合适的预聚合表，无需修改查询代码。

## 概述

### 工作原理

```
用户查询 → 匹配预聚合 → 重写 SQL → 查询预聚合表 → 返回结果
              ↓
          无匹配时
              ↓
        查询原始表
```

### 适用场景

- 大数据量的聚合查询（百万级以上）
- 固定维度组合的报表查询
- 对查询响应时间有严格要求

## 配置预聚合

在 TM 文件中添加 `preAggregations` 配置：

```javascript
export const model = {
    name: 'FactSalesModel',
    tableName: 'fact_sales',
    dimensions: [...],
    measures: [...],

    // 预聚合配置
    preAggregations: [
        {
            name: 'daily_product_sales',
            caption: '按产品日销售汇总',
            tableName: 'preagg_daily_product_sales',
            priority: 80,
            dimensions: ['salesDate', 'product'],
            granularity: {
                salesDate: 'day'
            },
            dimensionProperties: {
                salesDate: ['year', 'quarter', 'month'],
                product: ['category_name', 'brand']
            },
            measures: [
                { name: 'quantity', aggregation: 'SUM' },
                { name: 'salesAmount', aggregation: 'SUM' },
                { name: 'orderCount', aggregation: 'COUNT' }
            ],
            refresh: {
                strategy: 'INCREMENTAL',
                schedule: '0 2 * * *',
                watermarkColumn: 'salesDate$id',
                lookbackDays: 3
            },
            enabled: true
        }
    ]
};
```

## 配置项详解

### 基本配置

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 预聚合名称（唯一标识） |
| `caption` | string | 否 | 显示名称 |
| `tableName` | string | 是 | 预聚合表名 |
| `schema` | string | 否 | 数据库 schema |
| `priority` | int | 否 | 优先级 1-100，默认 50 |
| `enabled` | boolean | 否 | 是否启用，默认 true |

### 维度配置

| 参数 | 类型 | 说明 |
|------|------|------|
| `dimensions` | string[] | 包含的维度名称列表 |
| `granularity` | object | 时间维度的粒度配置 |
| `dimensionProperties` | object | 各维度包含的属性 |

**时间粒度选项**：

| 粒度 | 说明 | 示例 |
|------|------|------|
| `minute` | 分钟 | 2024-01-15 10:30 |
| `hour` | 小时 | 2024-01-15 10:00 |
| `day` | 天 | 2024-01-15 |
| `week` | 周 | 2024-W03 |
| `month` | 月 | 2024-01 |
| `quarter` | 季度 | 2024-Q1 |
| `year` | 年 | 2024 |

### 度量配置

```javascript
measures: [
    { name: 'quantity', aggregation: 'SUM' },
    { name: 'salesAmount', aggregation: 'SUM' },
    { name: 'orderCount', aggregation: 'COUNT' },
    { name: 'minPrice', aggregation: 'MIN' },
    { name: 'maxPrice', aggregation: 'MAX' }
]
```

**支持的聚合类型**：

| 聚合 | 说明 | 可 Rollup |
|------|------|-----------|
| `SUM` | 求和 | 是 |
| `COUNT` | 计数 | 是（转为 SUM） |
| `MIN` | 最小值 | 是 |
| `MAX` | 最大值 | 是 |
| `AVG` | 平均值 | 需要 SUM+COUNT |

### 刷新配置

```javascript
refresh: {
    strategy: 'INCREMENTAL',  // FULL | INCREMENTAL
    schedule: '0 2 * * *',    // Cron 表达式
    watermarkColumn: 'salesDate$id',
    lookbackDays: 3
}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `strategy` | string | 刷新策略：FULL（全量）、INCREMENTAL（增量） |
| `schedule` | string | Cron 表达式 |
| `watermarkColumn` | string | 水位线列（增量刷新使用） |
| `lookbackDays` | int | 回溯天数（处理延迟数据） |

### 过滤条件

可以为预聚合配置永久过滤条件：

```javascript
filters: [
    { column: 'orderStatus', operator: 'eq', value: 'COMPLETED' },
    { column: 'salesDate', operator: 'gte', value: '2023-01-01' }
]
```

## 匹配规则

系统根据以下规则自动匹配预聚合：

### 1. 维度匹配

查询的维度必须是预聚合维度的子集。

```
查询: [salesDate, product]
预聚合: [salesDate, product, customer]
结果: 匹配 ✓
```

### 2. 属性匹配

查询的维度属性必须在预聚合中存在。

```
查询: product$category_name
预聚合 dimensionProperties: { product: ['category_name', 'brand'] }
结果: 匹配 ✓
```

**注意**：`caption` 和 `id` 是隐式属性，无需显式配置。

### 3. 粒度匹配

查询粒度必须 >= 预聚合粒度（支持向上聚合）。

```
预聚合粒度: day
查询 salesDate$month: 匹配 ✓ (day → month 可 rollup)
查询 salesDate$hour: 不匹配 ✗ (day → hour 无法细化)
```

### 4. 度量匹配

查询的度量必须在预聚合中存在，且聚合方式兼容。

### 5. 优先级选择

当多个预聚合都满足条件时，按以下顺序选择：

1. `priority` 值更高
2. 维度数量更接近查询
3. 粒度更细

## 创建预聚合表

### 手动创建

根据配置手动创建预聚合表：

```sql
CREATE TABLE preagg_daily_product_sales (
    -- 维度列
    sales_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    product_category_name VARCHAR(100),
    product_brand VARCHAR(100),

    -- 度量列
    quantity_sum DECIMAL(20,4),
    sales_amount_sum DECIMAL(20,4),
    order_count BIGINT,

    -- 元数据列
    _preagg_created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    _preagg_updated_at TIMESTAMP,

    PRIMARY KEY (sales_date, product_id)
);

CREATE INDEX idx_preagg_daily_sales_date
    ON preagg_daily_product_sales(sales_date);
```

### 通过 API 创建

```
POST /api/pre-agg/{modelName}/{preAggName}/create-table
```

## 刷新预聚合

### 自动刷新

配置 `refresh.schedule` 后，系统会按 Cron 表达式自动刷新。

### 手动刷新

```
POST /api/pre-agg/{modelName}/{preAggName}/refresh
```

### 刷新策略

**全量刷新 (FULL)**：
```sql
TRUNCATE TABLE preagg_daily_product_sales;

INSERT INTO preagg_daily_product_sales (...)
SELECT DATE(order_date), product_id, SUM(quantity), ...
FROM fact_sales
LEFT JOIN dim_product ON ...
GROUP BY DATE(order_date), product_id;
```

**增量刷新 (INCREMENTAL)**：
```sql
-- 删除受影响的数据（含回溯）
DELETE FROM preagg_daily_product_sales
WHERE sales_date >= '2024-01-08';

-- 插入新数据
INSERT INTO preagg_daily_product_sales (...)
SELECT ... FROM fact_sales
WHERE order_date >= '2024-01-08'
GROUP BY ...;
```

## 混合查询

当预聚合数据可能不完整时（如增量刷新场景），系统会自动启用混合查询：

```
预聚合数据 (watermark 之前)
    UNION ALL
原始表数据 (watermark 之后)
```

### 水位线检测

系统通过 `watermarkColumn` 和 `dataWatermark` 判断数据时效性：

- 水位线为今天或之后：数据不过期
- 水位线在今天之前：启用混合查询

## 查看预聚合状态

### 查询结果中的信息

查询结果的 `extData` 会包含预聚合使用信息：

```json
{
  "data": [...],
  "extData": {
    "preAggUsed": "daily_product_sales",
    "preAggMode": "direct",  // direct | rollup | hybrid
    "originalSql": "..."
  }
}
```

### 管理 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/pre-agg/list` | GET | 列出所有预聚合 |
| `/api/pre-agg/{model}/{name}/status` | GET | 获取状态 |
| `/api/pre-agg/{model}/{name}/refresh` | POST | 手动刷新 |

## 配置示例

### 多粒度配置

为同一数据源配置多个粒度的预聚合：

```javascript
preAggregations: [
    {
        name: 'daily_sales',
        tableName: 'preagg_daily_sales',
        priority: 80,
        dimensions: ['salesDate', 'product'],
        granularity: { salesDate: 'day' },
        measures: [...]
    },
    {
        name: 'monthly_sales',
        tableName: 'preagg_monthly_sales',
        priority: 60,
        dimensions: ['salesDate', 'product'],
        granularity: { salesDate: 'month' },
        measures: [...]
    }
]
```

### 多维度组合

为不同的维度组合配置预聚合：

```javascript
preAggregations: [
    {
        name: 'product_sales',
        tableName: 'preagg_product_sales',
        priority: 70,
        dimensions: ['salesDate', 'product'],
        ...
    },
    {
        name: 'customer_sales',
        tableName: 'preagg_customer_sales',
        priority: 70,
        dimensions: ['salesDate', 'customer'],
        ...
    },
    {
        name: 'channel_sales',
        tableName: 'preagg_channel_sales',
        priority: 70,
        dimensions: ['salesDate', 'channel'],
        ...
    }
]
```

## 最佳实践

### 1. 分析查询模式

在配置预聚合前，分析实际的查询模式：

- 哪些维度组合最常用？
- 需要什么粒度的时间聚合？
- 有哪些度量需要预计算？

### 2. 合理设置优先级

- 细粒度预聚合设置更高优先级
- 常用查询对应的预聚合设置更高优先级

### 3. 平衡存储与性能

- 预聚合表会占用额外存储空间
- 粒度越细，存储占用越大
- 建议只为高频查询配置预聚合

### 4. 监控刷新状态

定期检查预聚合的刷新状态，确保数据时效性。

### 5. 结合缓存使用

预聚合 + 缓存可实现最佳性能：

- 预聚合减少 SQL 计算量
- 缓存避免重复查询

## 常见问题

### Q: 预聚合没有被使用？

检查以下几点：
1. `enabled` 是否为 `true`
2. 查询的维度/度量是否在预聚合中
3. 时间粒度是否满足要求
4. 查看日志中的匹配信息

### Q: 预聚合数据与原始数据不一致？

可能原因：
1. 预聚合未及时刷新
2. 增量刷新的 `lookbackDays` 设置过小
3. 过滤条件配置错误

### Q: 如何强制使用或禁用预聚合？

目前不支持在查询中强制指定。可以通过：
1. 调整 `priority` 影响匹配顺序
2. 设置 `enabled: false` 禁用特定预聚合
