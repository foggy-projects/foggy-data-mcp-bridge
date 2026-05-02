# 9.0.0 详细设计 (07)：S12 baselineRatio 受控基准引用执行计划

> **intended_for**：Foggy Java Core / MCP Schema 执行 Agent、验收评审 Agent。
>
> **purpose**：把 `baselineRatio` 从 S11 后续候选能力拆成可执行开发计划，明确公开 DSL、实现边界、Guardrail、测试矩阵和三库 SQL Parity 签收口径。

## 一、阶段定位

S12 的目标是实现 `baselineRatio`，用结构化 `pivot.metrics` 对象覆盖高频跨轴基准引用场景，替代对通用 `CELL_AT / AXIS_MEMBER` 的公开 DSL 需求。

本阶段不是重新开放 MDX 坐标系统。公开契约只表达业务意图：

> 当前单元格指标值 / 同一行坐标下列轴首个或末个基准成员的指标值。

对应 MDX 高频场景：

```mdx
[Measures].[Sales] / ( [Measures].[Sales], Axis(0).Item(0) )
```

Foggy 不暴露 `Axis(0).Item(0)`，而使用强类型 JSON：

```json
{
  "pivot": {
    "rows": ["product$category"],
    "columns": ["salesDate$month"],
    "metrics": [
      "salesAmount",
      {
        "name": "salesIndex",
        "type": "baselineRatio",
        "of": "salesAmount",
        "axis": "columns",
        "baseline": "first"
      }
    ],
    "outputFormat": "grid"
  }
}
```

## 二、范围边界

### 本阶段支持

1. `pivot.metrics` 混合数组中新增对象类型 `baselineRatio`。
2. 第一版仅支持：
   - `axis = "columns"`。
   - `baseline = "first" | "last"`。
   - `of` 引用同一 `pivot.metrics` 中已经声明的原生度量。
3. 计算发生在 Pivot 内存结果集上，不要求 SQL 下推。
4. 派生值只作为输出指标，不参与 `having / orderBy / limit / rollup`。
5. `null`、缺失基准、基准值为 0 时返回 `null`。
6. `systemSlice`、用户 `slice`、`fieldAccess`、`deniedColumns` 必须沿用 Phase 1 查询链路，不允许派生指标绕过权限。

### 本阶段不支持

1. 通用 `CELL_AT(row, column, metric)`。
2. `AXIS_MEMBER(axis, index)` 或裸 index API。
3. `axis = "rows"`。
4. 固定 member path，例如 `baseline = {"mode":"member","path":{...}}`。
5. 引用 subtotal / grandTotal 作为基准。
6. `hierarchyMode = "tree"`。
7. 多个派生指标互相引用。
8. 在顶层 `calculatedFields` 中使用 `CELL_AT / AXIS_MEMBER / AXIS_REF`。

这些限制必须 fail-closed，而不是静默降级。

## 三、公开 DSL 契约

### Metric 对象形态

`pivot.metrics` 继续是字符串与对象混合数组：

```json
[
  "salesAmount",
  {
    "name": "salesIndex",
    "type": "baselineRatio",
    "of": "salesAmount",
    "axis": "columns",
    "baseline": "first"
  }
]
```

### 字段规则

| 字段 | 必填 | 允许值 | 说明 |
|---|---:|---|---|
| `name` | 是 | 字符串 | 输出指标名，不能与任何原生或派生指标重名 |
| `type` | 是 | `baselineRatio` | 派生类型 |
| `of` | 是 | 原生度量名 | 分子和分母使用的基础度量 |
| `axis` | 是 | `columns` | 第一版只允许列轴基准 |
| `baseline` | 是 | `first` / `last` | 使用列轴第一个或最后一个成员作为基准 |

### Schema 建议

当前 S11 Schema 对对象 metric 只有 `parentShare` 一种形态。S12 不建议继续在同一个 object schema 上堆 enum，而应改成 `oneOf` 两个分支：

1. `parentShareMetric`：
   - `type.const = "parentShare"`
   - `required = ["name", "type", "of"]`
   - `axis.enum = ["rows"]`
   - `additionalProperties = false`
2. `baselineRatioMetric`：
   - `type.const = "baselineRatio"`
   - `required = ["name", "type", "of", "axis", "baseline"]`
   - `axis.const = "columns"`
   - `baseline.enum = ["first", "last"]`
   - `additionalProperties = false`

这样能在 MCP Gateway 前置拦截 `expr`、`axis=rows`、缺失 `baseline`、多余 `path/member/index` 等非法结构。

## 四、执行语义

### 计算定义

对于每个普通 Pivot 单元格：

```text
baselineRatio = current[of] / baselineCell[of]
```

其中：

1. `current` 是当前行列坐标下的聚合结果。
2. `baselineCell` 是相同行轴坐标下，列轴 domain 中 `first` 或 `last` 成员对应的单元格。
3. 列轴 domain 以 Phase 2 处理后的结果集为准，必须与用户最终看到的 Pivot 坐标集合一致。

### 执行阶段

推荐新增 `BaselineRatioCalculator`，执行在：

```text
Phase 2.6 SubtotalInjector
Phase 2.7 PropertyAttacher
Phase 2.8 ParentShareCalculator
Phase 2.9 BaselineRatioCalculator
Phase 3   ResultShaper
```

说明：

1. `baselineRatio` 只读取 `of` 指标，不依赖 `parentShare` 输出，放在 `ParentShareCalculator` 之后可以减少对现有阶段编号的扰动。
2. `ResultShaper` 已使用 `pivot.getAllOutputMetricNames()`，应能自然带出新派生指标。
3. subtotal / grandTotal 行必须写入 `null`，不能参与基准计算。

### 内存算法

建议流程：

```text
1. 提取 rowFields / colFields。
2. 从非 subtotal 行构造全局 column domain，保持现有结果顺序。
3. 按 rowFields 构造 rowKey，把同一行坐标下的单元格归组。
4. 对每组定位 baseline column key：
   - baseline=first -> column domain 第一个成员
   - baseline=last  -> column domain 最后一个成员
5. 对每个普通单元格计算 current / baseline。
6. 缺失、null、非数值、除零输出 null。
```

复杂度：

```text
O(cellCount * baselineMetricCount)
```

`cellCount` 已由 `CardinalityBreaker` 控制。可额外限制派生 metric 数量，例如不超过 10 个。

## 五、代码改动清单

### Java Core

1. `PivotMetricItem`
   - 新增字段 `baseline`。
   - 新增 `isBaselineRatio()`。
   - `validate()` 支持 `parentShare` 与 `baselineRatio` 两类对象。
   - `baselineRatio` 必须拒绝 `expr`、缺失 `of`、缺失 `axis`、非 `columns`、缺失/非法 `baseline`、携带 `level/parentLevel`。
2. `PivotRequest`
   - 新增 `getBaselineRatioMetrics()`。
   - `getSqlMetricNames()` 应继续把 `baselineRatio.of` 纳入 SQL metrics。
   - `getAllOutputMetricNames()` 应包含 `baselineRatio.name`。
3. 新增 `BaselineRatioCalculator`
   - 包名建议：`com.foggyframework.dataset.db.model.engine.pivot.algo`。
   - 复用 `_sys_meta` 判断逻辑，遇到 subtotal / grandTotal 输出 `null`。
4. `PivotPipeline`
   - 增加 `baselineRatio` 守卫：
     - `tree + baselineRatio` fail-closed。
     - `columns` 为空 fail-closed。
     - `of` 不在原生 metrics 中 fail-closed。
   - 在 Phase 2.9 调用 `BaselineRatioCalculator.apply(...)`。
5. 权限链路
   - 不需要新增权限入口。
   - 必须确认 `baselineRatio.of` 进入 SQL metrics 后，现有 `fieldAccess / deniedColumns` 能覆盖。

### MCP Schema / Prompt

1. `query_model_v3_schema.json`
   - 将 metric object 改为 `oneOf(parentShareMetric, baselineRatioMetric)`。
   - 保持 `additionalProperties=false`。
2. `query_model_v3.md`
   - 新增 `baselineRatio` 标准示例。
   - 明确禁止 `CELL_AT / AXIS_MEMBER / AXIS_REF`。
   - 明确 `baselineRatio` 第一版只支持列轴 `first/last`。

### 版本文档

实现后同步更新：

1. `06_s11_metrics_unification_and_derived_metrics.md`
2. `05_cell_at_cross_axis_evaluation.md`
3. `mdx_vs_foggy_syntax_comparison.md`
4. `acceptance/version-signoff.md`
5. `README.md`

## 六、测试矩阵

### 单元测试

1. `PivotMetricItemTest`
   - 合法 `baselineRatio` 通过。
   - 缺失 `baseline` 拒绝。
   - `axis=rows` 拒绝。
   - `baseline=middle` 拒绝。
   - 携带 `level/parentLevel/expr` 拒绝。
2. `BaselineRatioCalculatorTest`
   - `baseline=first`。
   - `baseline=last`。
   - 多个 row group 相互隔离。
   - 基准缺失输出 `null`。
   - 基准为 0 输出 `null`。
   - subtotal / grandTotal 输出 `null`。

### SQLite 集成测试

1. 直接执行 Pivot 查询，输出 `baselineRatio`。
2. grid / flat 至少各覆盖一种。
3. `systemSlice` 与用户 `slice` 生效。
4. `hierarchyMode=tree + baselineRatio` fail-closed。

### SQL Parity

必须直接执行 Foggy Pivot 查询，并与独立 SQL oracle 做真实结果比对。不能只断言“有字段”或“非空”。

SQL oracle 推荐：

```sql
WITH base AS (
  SELECT
    category_name,
    sales_month,
    SUM(sales_amount) AS sales_amount
  FROM ...
  WHERE ...
  GROUP BY category_name, sales_month
),
ranked AS (
  SELECT
    *,
    FIRST_VALUE(sales_amount) OVER (
      PARTITION BY category_name
      ORDER BY sales_month
    ) AS first_sales,
    LAST_VALUE(sales_amount) OVER (
      PARTITION BY category_name
      ORDER BY sales_month
      ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) AS last_sales
  FROM base
)
SELECT
  category_name,
  sales_month,
  sales_amount,
  sales_amount / NULLIF(first_sales, 0) AS sales_index_first,
  sales_amount / NULLIF(last_sales, 0) AS sales_index_last
FROM ranked;
```

必须覆盖：

1. SQLite。
2. MySQL8。
3. PostgreSQL。
4. `systemSlice` 条件下的 parity。
5. `deniedColumns` 命中 `of` 底层物理列时 fail-closed。

### MCP Schema / Controller

1. 合法 `baselineRatio` 通过 Schema。
2. 缺失 `baseline` 拒绝。
3. `axis=rows` 拒绝。
4. 额外字段 `path/member/index/expr` 拒绝。
5. JSON-RPC 错误包装可读。

## 七、验收标准

S12 只有同时满足以下条件才可签收：

1. Java Core、MCP Schema、Prompt 文档均完成。
2. SQLite / MySQL8 / PostgreSQL 三库 SQL Parity 通过。
3. Parity 必须是“直接执行 Pivot 查询”与“独立 SQL 查询结果”的逐格比对。
4. `systemSlice`、用户 `slice`、`deniedColumns` 权限测试均覆盖。
5. `CELL_AT / AXIS_MEMBER / AXIS_REF` 仍未出现在公开 Schema 的可生成结构中。
6. `expr` 对象指标仍保持 fail-closed，不因 S12 回退开放。
7. `version-signoff.md` 写入 S12 证据和剩余边界。

## 八、建议执行顺序

1. S12.1：AST 与 Schema 扩展。
2. S12.2：`BaselineRatioCalculator` 内存算法。
3. S12.3：Pipeline 接入与 Guardrail。
4. S12.4：SQLite 单元 / 集成 / SQL Parity。
5. S12.5：MySQL8 / PostgreSQL SQL Parity。
6. S12.6：MCP Prompt、MDX 对比文档和 Signoff 更新。

第一轮实现不要做 SQL CTE 下推。CTE / Window 可以作为后续 optimizer，不作为 S12 的语义来源。
