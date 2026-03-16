# Foggy Data MCP Bridge — Roadmap

## fsscript QM Compose（跨模型查询编排）

### 背景

单个 QM 内部已 JOIN 大量 TM（如 TMS 运单 QM 由十几个 TM JOIN 而成），权限控制在 QM 层。QM 对外等价于一个视图——外部消费者不需要关心内部 JOIN 细节。

QM Compose 解决的是**QM 之间的组合查询**——用 fsscript 编排多个 QM，支持 JOIN、参数传递、二次计算。

### 三种组合模式

#### 模式 1：结果集 JOIN（内存合并）

两个 QM 各自执行 SQL，Java 侧对结果集做 hash join。

```javascript
const orders = dsl({
    model: 'SaleOrderQM',
    columns: ['partner$id', 'partner$caption', 'sum(amountTotal) as totalSales'],
    orderBy: ['-totalSales'],
    limit: 10
});
const leads = dsl({
    model: 'CrmLeadQM',
    columns: ['partner$id', 'count(id) as leadCount', 'sum(expectedRevenue) as pipeline'],
});
return orders.leftJoin(leads, 'partner$id');
```

适合：两边聚合后数据量小（几十~几百行）、跨数据源（JDBC + MongoDB）。

#### 模式 2：结果作为参数（ID 下推）

第一个 QM 的结果注入到第二个 QM 的 slice 中。

```javascript
const topCustomers = dsl({
    model: 'SaleOrderQM',
    columns: ['partner$id'],
    orderBy: ['-amountTotal'],
    limit: 10
});
const leads = dsl({
    model: 'CrmLeadQM',
    columns: ['partner$caption', 'stage$caption', 'expectedRevenue'],
    slice: [{ field: 'partner$id', op: 'in', value: topCustomers.column('partner$id') }]
});
return leads;
```

适合：第一步筛选 ID 列表，第二步按 ID 过滤明细。单次 SQL 往返，数据库侧计算。

#### 模式 3：CTE/视图 JOIN（SQL 层合并）

将两个 QM 各自生成的 SQL 包装为 CTE，在 SQL 层直接 JOIN。QM 对外等价于视图，动态 JOIN 层不需要关心各自内部 TM 细节。

```javascript
const composed = dsl({
    model: 'SaleOrderQM',
    columns: ['partner$caption', 'sum(amountTotal) as totalSales'],
}).withJoin({
    model: 'CrmLeadQM',
    on: 'partner$id',
    columns: ['count(id) as leadCount'],
    type: 'left'
});
return composed;
```

引擎生成的 SQL 结构：

```sql
WITH cte_orders AS (
    -- SaleOrderQM 完整 SQL（含内部 TM JOIN + 权限 slice）
    SELECT partner_id, partner_name, SUM(amount_total) AS totalSales
    FROM sale_order JOIN res_partner ON ...
    WHERE ...  -- 权限 slice 已注入
    GROUP BY partner_id, partner_name
),
cte_leads AS (
    -- CrmLeadQM 完整 SQL（含内部 TM JOIN + 权限 slice）
    SELECT partner_id, COUNT(*) AS leadCount
    FROM crm_lead JOIN res_partner ON ...
    WHERE ...  -- 权限 slice 已注入
    GROUP BY partner_id
)
SELECT o.partner_name, o.totalSales, l.leadCount
FROM cte_orders o
LEFT JOIN cte_leads l ON o.partner_id = l.partner_id
```

优点：单条 SQL，数据库优化器统一处理；各 QM 的权限 slice 在各自 CTE 内独立注入，不交叉。

适合：同库查询、性能敏感场景、大数据量。

### 权限隔离

三种模式下权限 slice 都在**各自 QM 内部独立注入**，不交叉：
- 模式 1/2：各自执行时各自注入
- 模式 3：各自 CTE 内各自注入

这是 QM 层做组合的核心优势——TM 层自动 JOIN 无法保证这一点。

### fsscript DataSet API 设计

```javascript
// 创建查询
const ds = dsl({ model, columns, slice, orderBy, limit, ... });

// 执行与取值
ds.execute()              // → ResultSet
ds.column('field')        // → 单列值数组（用于注入下一个查询的 slice）

// 内存组合（模式 1）
ds1.leftJoin(ds2, 'key')
ds1.innerJoin(ds2, 'key')
ds1.union(ds2)

// SQL 层组合（模式 3）
ds1.withJoin({ model, on, columns, type })

// 二次计算
ds.compute('avgDeal', 'totalSales / leadCount')
ds.filter('totalSales > 10000')
ds.sort('-totalSales')
```

### 与业界方案对比

| 方案 | 跨模型实现 | 权限模型 |
|------|-----------|---------|
| **Cube.js** | cube schema `joins` 声明，查询时自动 JOIN | 按 cube 粒度，无行级权限 |
| **Looker (LookML)** | `explore` + `join` 声明，支持多种关系类型 | 按 explore/model 粒度 |
| **dbt Semantic Layer** | MetricFlow entities + relationships，编译时 JOIN | 无运行时权限 |
| **Foggy (planned)** | fsscript 编程式，支持内存/SQL 两种合并 | QM 层行级权限独立注入 |

Foggy 的区分点：**编程式组合 + QM 级行级权限隔离**。声明式方案（Cube.js/Looker）在简单场景更便捷，但难以处理"同一事实表、不同业务场景、不同权限要求"的情况。

## 日期维度表

独立日历参考表，不修改业务表结构。

```sql
CREATE TABLE dim_date (
    date_id      INTEGER PRIMARY KEY,  -- YYYYMMDD
    full_date    DATE NOT NULL,
    year         SMALLINT,
    quarter      SMALLINT,
    month        SMALLINT,
    week_of_year SMALLINT,
    day_of_week  SMALLINT,
    is_weekend   BOOLEAN,
    month_name   VARCHAR(20),
    quarter_name VARCHAR(10)
);
-- 一次性灌 10 年数据（~3650 行），永不变
```

TM 中日期维度指向 dim_date，引擎自动 JOIN。支持"Q1 销售额"、"周末订单占比"、"去年同期对比"等自然语言时间表达。

## QM 预定义计算指标

在 QM calculatedFields 中预定义高频业务指标，LLM 直接引用：
- 客单价 = amountTotal / orderCount
- 赢单率 = won_count / total_count
- 毛利率 = (revenue - cost) / revenue
- 平均回款天数 = avg(payment_date - invoice_date)
