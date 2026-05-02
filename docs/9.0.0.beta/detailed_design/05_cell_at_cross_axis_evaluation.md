# 9.0.0 详细设计评估 (05)：CELL_AT / AXIS_MEMBER 跨轴坐标引用

本文是后续增强评估文档，用于判断 `CELL_AT` / `AXIS_MEMBER` 这类 MDX 跨轴坐标引用能力是否应该进入 Foggy Pivot 的正式能力范围。

结论先行：

1. 通用 `CELL_AT(row, column, metric)` 和 `AXIS_MEMBER(axis, index)` 状态为 **`rejected-for-public-dsl`**——不作为 LLM 可生成的公开 DSL 暴露。
2. 高频跨轴引用场景通过结构化 `pivot.metrics` 派生指标受控覆盖：`parentShare`（父级占比）和 `baselineRatio`（基准引用），优先采用**内存后置计算**。
3. 对 LLM 可见的接口不暴露 MDX 式坐标系统和函数字符串，统一收敛到 `pivot.metrics` 对象结构。
4. 统一指标设计见 `06_s11_metrics_unification_and_derived_metrics.md`，`baselineRatio` 执行计划见 `07_s12_baseline_ratio_execution_plan.md`。

---

## 一、问题定义

MDX 可以在当前单元格中引用另一个单元格：

```mdx
[Measures].[Sales] /
( [Measures].[Sales], Axis(0).Item(0) )
```

典型业务语义包括：

1. 当前月份销售额 / 首月销售额，生成销售指数。
2. 当前月份销售额 / 固定基准月份销售额，生成基期比。
3. 当前格 / 同行或同列某个基准成员值。
4. 当前格 / 小计格或总计格，生成占比。

这类能力和现有几个语义不同：

| 能力 | 语义 | 是否等价于跨轴坐标 |
|---|---|---|
| `REMOVE_FILTERS(dim)` | 移除过滤上下文，适合全局占比 | 否 |
| `ROLLUP_TO(parent)` | 回到当前成员父级坐标，适合父子占比 | 否 |
| `Generate + TopN` | 对每个分区生成非均匀成员集合 | 否 |
| `CELL_AT / AXIS_MEMBER` | 显式引用 Pivot 坐标系里的另一个格子 | 是 |

因此这不是普通聚合问题，而是**坐标系统设计问题**。如果直接开放，会把 MDX 最复杂的一部分重新引入 JSON DSL。

---

## 二、候选方案

### 方案 A：SQL CTE / Window 下推

窄场景示例：每个品类下，各月份销售额除以首月销售额。

```sql
WITH base AS (
  SELECT
    product_category,
    sales_month,
    SUM(sales_amount) AS sales_amount
  FROM fact_sales
  GROUP BY product_category, sales_month
),
ranked AS (
  SELECT
    *,
    FIRST_VALUE(sales_amount) OVER (
      PARTITION BY product_category
      ORDER BY sales_month
    ) AS baseline_sales
  FROM base
)
SELECT
  product_category,
  sales_month,
  sales_amount,
  sales_amount / NULLIF(baseline_sales, 0) AS sales_index
FROM ranked;
```

优点：

1. 性能好，数据库负责窗口计算。
2. 与“首列基准”“固定排序基准”这类需求天然贴合。
3. SQLite、MySQL8、PostgreSQL 都具备窗口函数能力，容易构造 SQL Parity 测试。

缺点：

1. 与 Pivot 当前“SQL 轻聚合 + 内存重塑形”的架构方向不完全一致。
2. 多层列轴存在歧义：`index = 0` 到底指第一个 year、quarter，还是第一个 month leaf？
3. 与 `TopN`、`CrossJoin`、`Subtotal` 的执行顺序可能不一致。
4. 方言细节仍需验证，例如 `NULL` 排序、窗口 frame 默认行为、别名引用能力。

LLM 生成风险：中。

如果让 LLM 生成窗口 SQL，风险高；如果只让 LLM 表达“基准列”，由引擎下推，风险可控。

### 方案 B：Pivot 内存后置计算

基础思路：在 Pivot 已经形成内存结果集后，建立坐标索引，再计算新增指标。

推荐阶段：

```text
Phase 1   SQL aggregate
Phase 2.1 Having
Phase 2.2 TopN
Phase 2.3 Rollup planning / auxiliary query
Phase 2.5 CrossJoin
Phase 2.6 Subtotal
Phase 2.7 Pivot derived metrics
Phase 2.8 Properties attach
Phase 3   Result shaping
```

核心索引：

```java
Map<CellCoord, Map<String, Object>> cubeIndex;
```

伪流程：

```text
for each currentCell in cube:
  refCoord = resolveReference(currentCell, derivedMetric.reference)
  refValue = cubeIndex.get(refCoord).get(metric)
  currentCell.put(derivedMetric.name, divideOrNull(currentValue, refValue))
```

优点：

1. 与用户最终看到的 Pivot 坐标集合一致。
2. 不依赖数据库方言。
3. 能自然感知 `TopN` 和 `CrossJoin` 后的成员集合。
4. 更符合当前 Java Core 既有流水线。

缺点：

1. 必须定义清楚坐标语义，否则会退化成 MDX。
2. 计算复杂度约为 `O(cells * derivedMetricCount)`，必须受 `CardinalityBreaker` 管控。
3. 需要处理循环依赖、缺失引用、除零、subtotal/grandTotal 引用等边界。
4. 不适合作为 `having/orderBy/limit` 输入，除非重新定义流水线顺序。

LLM 生成风险：

1. 如果暴露通用 `CELL_AT(row=..., column=...)`，风险中高。
2. 如果暴露结构化的受控基准引用，风险中低。

### 方案 C：混合执行

对 LLM 暴露统一 DSL，由引擎决定执行层。

| 场景 | 推荐执行层 | 说明 |
|---|---|---|
| 同一 row 下引用首列基准 | 内存优先，SQL 可优化 | 内存语义贴近最终 Pivot，SQL 性能更好 |
| 同一 row 下引用固定 column 成员 | 内存优先 | 避免 SQL self join 和方言差异 |
| 当前成员占父级 | `ROLLUP_TO` / RollupCache | 不应归入 `CELL_AT` |
| 当前格占 grandTotal | RollupCache 或内存 lookup | 依赖 subtotal 注入顺序 |
| 跨 row 任意引用 | 不开放 | LLM 误用风险高 |
| 多层轴 index 引用 | 不开放或必须显式 path | 默认 index 语义歧义 |

优点是保留优化空间；缺点是 planner 和 explain 成本上升。若采用混合方案，必须在 explain 中写明每个派生指标的执行层。

### 方案 D：退回 compose_script

复杂坐标漫游、跨模型计算、最终 shaped result 二次加工可以退回 `compose_script`。

该方案适合作为兜底，不适合作为高频 BI 能力主路径。原因是 LLM 需要同时理解 Pivot 输出结构和脚本 API，生成正确率通常低于结构化 Pivot DSL。

---

## 三、推荐的第一版能力边界

不开放通用 `CELL_AT` / `AXIS_MEMBER`。推荐第一版只做**同行/同列基准引用**，并使用 S11 定义的 `pivot.metrics` 对象元素表达。不要再新增独立的 `derivedMetrics` 块。

推荐形态：

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

固定成员引用可以作为第二步：

```json
{
  "name": "salesIndexToJan",
  "type": "baselineRatio",
  "of": "salesAmount",
  "axis": "columns",
  "baseline": {
    "mode": "member",
    "path": {
      "salesDate$month": "2024-01"
    }
  }
}
```

第一版支持：

1. `type = baselineRatio`。
2. `axis = columns`：在相同行轴坐标下引用列轴基准。
3. `axis = rows`：在相同列轴坐标下引用行轴基准。
4. `reference.mode = first | last | member`。
5. `baseline.mode = member` 时，`path` 必须覆盖被引用轴的全部层级字段。
6. 引用不存在、引用值为 `null`、除零时返回 `null`。
7. 只生成输出指标，不允许参与 `having/orderBy/limit`。

第一版不支持：

1. 任意 `CELL_AT(row, column, metric)`。
2. `AXIS_MEMBER("columns", 0)` 这类裸 index API。
3. 跨 row + 跨 column 同时改写坐标。
4. 引用 subtotal / grandTotal。
5. `hierarchyMode=tree`。
6. 多个派生 metric 互相引用。
7. 作为小计/总计的可加度量参与 rollup。

---

## 四、为什么不建议放进 calculatedFields

`calculatedFields` 当前更适合表达聚合后的简单算术或可下推表达式。如果把 `CELL_AT` 放进字符串表达式：

```json
{
  "name": "salesIndex",
  "expression": "salesAmount / NULLIF(CALCULATE(salesAmount, AXIS_MEMBER('columns', 0)), 0)"
}
```

会带来三个问题：

1. LLM 容易把内部函数当成通用 MDX 坐标能力使用。
2. 表达式解析器必须理解 Pivot 轴、执行阶段和坐标解析。
3. Schema 很难对 `axis/path/reference` 做强类型约束。

因此建议在 `pivot.metrics` 中支持结构化对象元素，例如 `{ "name": "...", "type": "baselineRatio", "of": "..." }`。这比在表达式字符串里扩展隐藏函数更利于 Guardrail、错误提示和 LLM 稳定生成，也避免 `metrics + calculatedFields + derivedMetrics` 三套结构并存。

---

## 五、实现影响评估

### AST / Schema

S11 建议新增统一的 `PivotMetricDefinition`，用于承载原生度量、算术指标与结构化派生指标：

```java
class PivotMetricDefinition {
    String name;
    String expr;      // arithmetic metric, mutually exclusive with type
    String type;      // baselineRatio
    String of;        // base metric
    String axis;      // rows / columns
    Object baseline;  // first / last / member path
}
```

Schema 约束：

1. `type` 使用枚举。
2. `axis` 使用枚举。
3. `baseline.mode = member` 时必须提供 `path`。
4. `baseline = first/last` 时禁止提供裸 `index`。
5. `CELL_AT`、`AXIS_MEMBER`、`AXIS_REF` 继续在 Prompt 和 Schema 描述中标记为禁止生成。

### Pipeline

建议新增 `PivotDerivedMetricCalculator`：

```text
resultSet -> build rowDomain/colDomain -> build cubeIndex -> evaluate derived metrics -> resultSet
```

执行顺序建议放在 `SubtotalInjector` 之后、`PropertyAttacher` 之前。第一版不处理 subtotal 行，遇到 `_sys_meta` 直接跳过或输出 `null`。

如后续需要支持 subtotal 基准，应单独设计 derived metric 与 rollup 的关系，不能默认把比率类指标求和。

### 权限

派生指标引用的 `metric` 必须已经通过原始 `metrics` 声明，不能绕过 `deniedColumns` 获取隐藏度量。

`member.path` 中出现的字段必须已经在对应轴上声明，不能借由 path 引入未授权维度。

`systemSlice`、`deniedColumns`、用户 `slice` 均由 Phase 1 查询继承。内存后置计算只能使用 Phase 1 返回的数据，不发起绕过权限的新查询。

### 性能

内存计算复杂度：

```text
O(cellCount * derivedMetricCount)
```

`cellCount` 已受 `CardinalityBreaker` 保护。建议对 derived metric 数量另加小上限，例如 10。

---

## 六、测试验收要求

实现前必须补齐以下测试，不满足则不进入签收。

### 单元测试

1. `PivotCellIndexTest`：坐标 key 构造、null key、重复 key fail-closed。
2. `PivotDerivedMetricCalculatorTest`：first/last/member 基准、缺失引用、除零、null。
3. 多层轴 `member.path` 未覆盖全部层级时拒绝。
4. `tree`、subtotal 引用、derived metric 互相引用均 fail-closed。
5. 权限绕过测试：`metric` 不在 `metrics` 或命中 denied 字段时拒绝。

### 集成 / SQL Parity

必须直接执行 Pivot 查询，并与真实 SQL oracle 比对：

1. SQLite：基础基准比率。
2. MySQL8：同一用例对齐。
3. PostgreSQL：同一用例对齐。
4. `systemSlice` 生效时，Pivot 派生值与带相同 where 条件的 SQL CTE 一致。
5. `deniedColumns` 命中时，Pivot 在网关或引擎前置失败，不能返回派生值。
6. `TopN` 后计算基准时，结果必须与“先 TopN 后 lookup”的 SQL / 内存 oracle 一致。

SQL oracle 推荐使用 CTE / window：

```sql
WITH base AS (...),
ranked AS (
  SELECT
    *,
    FIRST_VALUE(sales_amount) OVER (
      PARTITION BY product_category
      ORDER BY sales_month
    ) AS baseline_sales
  FROM base
)
SELECT ..., sales_amount / NULLIF(baseline_sales, 0) AS sales_index
FROM ranked;
```

---

## 七、LLM 心智负担评估

| 方案 | LLM 需要理解 | 易错点 | 正确率预估 | 结论 |
|---|---|---|---|---|
| 直接生成 CTE / Window | SQL 方言、分区、排序 | 方言差异、权限绕过 | 中低 | 不暴露 |
| `AXIS_MEMBER(axis,index)` | 轴序号、轴排序、层级含义 | 多层轴 index 歧义 | 中低 | 不开放 |
| 通用 `CELL_AT` | row/column 坐标系统 | 坐标漫游、subtotal 误用 | 低 | 不开放 |
| `calculatedFields` 内嵌隐藏函数 | 表达式 + Pivot 坐标 | Schema 难约束 | 中低 | 不推荐 |
| 结构化 `pivot.metrics` 对象元素 | 基准轴、基准成员、指标名 | member path 填写 | 中高 | 推荐 |
| `compose_script` | Pivot 输出结构 + 脚本 API | 步骤遗漏、结构误读 | 中低 | 兜底 |

面向 LLM 的核心原则：

1. 暴露业务意图，不暴露坐标漫游。
2. 使用 JSON Schema 做强约束，不依赖 Prompt 约束复杂表达式。
3. 第一版只允许最常见的基准比率，不把能力自然扩散到完整 MDX。

---

## 八、最终建议

### 设计决策（S11 Follow-Up）

1. `CELL_AT`：`rejected-for-public-dsl`。不作为 LLM 可生成的公开 DSL。
2. `AXIS_MEMBER`：`rejected-for-public-dsl`。多层轴 index 语义天然歧义，不开放。
3. 高频跨轴引用场景统一收敛到 `pivot.metrics` 结构化派生指标：
   - `parentShare`：覆盖父级占比（替代 `ROLLUP_TO`）。
   - `baselineRatio`：覆盖基准引用（替代 `CELL_AT` 受控子集）。
4. 不在 MCP Schema 中暴露 `ROLLUP_TO / CELL_AT / AXIS_MEMBER` 函数字符串。
5. 顶层 `calculatedFields` 在 Pivot 模式下仅兼容，不推荐。

### 实现路径

1. `baselineRatio`：第一版支持 `first / last`，内存后置计算，三库 SQL Parity。
2. `parentShare`：第一版支持同一轴相邻层级，复用 RollupCache。
3. SQL CTE / Window 可作为后续 optimizer，不作为第一版语义来源。

### 后续跟进

统一指标设计、DSL 契约和总体取舍见 `06_s11_metrics_unification_and_derived_metrics.md`；`baselineRatio` 的执行拆解、验收矩阵和 Antigravity 开工提示词见 `07_s12_baseline_ratio_execution_plan.md` 与 `../acceptance/s12_baseline_ratio_antigravity_prompt.md`。

当前决策：`rejected-for-public-dsl(CELL_AT, AXIS_MEMBER); implement-as-structured-dsl(parentShare, baselineRatio)`.
