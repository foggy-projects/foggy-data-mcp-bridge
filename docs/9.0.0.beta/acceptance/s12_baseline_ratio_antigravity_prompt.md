# Google Antigravity 开工提示词：S12 baselineRatio

你是一个在本地 IDE 中工作的 Java 工程 Agent。请在以下仓库中推进 Foggy Pivot V9 的 S12 阶段：

```text
D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge-wt-dev-compose
```

重要约束：

1. 不要假设你能看到此前对话、Codex/Claude 记忆、本地 skills 或项目说明文件。开始前必须读取下方“必读文档”。
2. 开始编辑前先执行 `git status --short`，识别已有未提交变更；不要回滚、覆盖或格式化无关文件。
3. 只修改 S12 相关代码、Schema、Prompt、测试和版本文档。
4. 如果 MySQL8 或 PostgreSQL 环境不可用，必须在进度文档和最终报告中记录原因，不得把 SQLite 通过标记为三库签收。

## 任务目标

实现结构化 `pivot.metrics` 派生指标 `baselineRatio`，覆盖高频跨轴基准引用场景：

> 当前单元格指标值 / 同一行坐标下列轴首个或末个基准成员的指标值。

公开 DSL 示例：

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

不要开放通用 `CELL_AT`、`AXIS_MEMBER`、`AXIS_REF`，也不要重新开放 `{ "name": "...", "expr": "..." }` 这种对象指标。

## 必读文档

请先阅读：

1. `CLAUDE.md`
2. `docs/9.0.0.beta/detailed_design/07_s12_baseline_ratio_execution_plan.md`
3. `docs/9.0.0.beta/detailed_design/06_s11_metrics_unification_and_derived_metrics.md`
4. `docs/9.0.0.beta/detailed_design/05_cell_at_cross_axis_evaluation.md`
5. `docs/9.0.0.beta/acceptance/s12_baseline_ratio_progress.md`

## 当前实现背景

S11 已完成：

1. `pivot.metrics` 支持字符串和对象混合数组。
2. `parentShare` 第一版已签收。
3. `expr` 对象指标被 Schema 和 runtime 双层 fail-closed。
4. `parentShare` 已覆盖 SQLite / MySQL8 / PostgreSQL SQL Parity。

关键类：

```text
foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/domain/pivot/PivotMetricItem.java
foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/domain/pivot/PivotRequest.java
foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/domain/pivot/PivotMetricsDeserializer.java
foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotPipeline.java
foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/algo/ParentShareCalculator.java
foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/algo/ResultShaper.java
foggy-dataset-mcp/src/main/resources/schemas/query_model_v3_schema.json
foggy-dataset-mcp/src/main/resources/schemas/descriptions/query_model_v3.md
```

## 实现范围

第一版只支持：

1. `type = "baselineRatio"`。
2. `axis = "columns"`。
3. `baseline = "first" | "last"`。
4. `of` 必须引用同一个 `pivot.metrics` 中已经声明的原生度量。
5. 缺失基准、基准为 `null`、基准为 0、当前值非数值时输出 `null`。
6. subtotal / grandTotal 行输出 `null`。

必须 fail-closed：

1. `axis = "rows"`。
2. `hierarchyMode = "tree" + baselineRatio`。
3. 缺失 `baseline`。
4. `baseline` 不是 `first/last`。
5. `of` 没有在原生 metrics 中声明。
6. 携带 `expr/path/member/index/level/parentLevel` 等第一版不支持字段。
7. 试图使用 `CELL_AT / AXIS_MEMBER / AXIS_REF`。

## 建议实现步骤

### 1. AST 与校验

修改 `PivotMetricItem`：

1. 新增字段 `baseline`。
2. 新增 `isBaselineRatio()`。
3. `validate()` 支持 `parentShare` 和 `baselineRatio`。
4. 对 `baselineRatio` 执行严格校验。

修改 `PivotRequest`：

1. 新增 `getBaselineRatioMetrics()`。
2. 确认 `getSqlMetricNames()` 会把 `baselineRatio.of` 加入 SQL metrics。
3. 确认 `getAllOutputMetricNames()` 会输出 `baselineRatio.name`。

### 2. 内存算法

新增：

```text
foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/algo/BaselineRatioCalculator.java
```

建议算法：

1. 从非 subtotal 行构造列轴 domain，保持当前结果集顺序。
2. 按 rowFields 构造 rowKey，把同一行坐标下的单元格归组。
3. 根据 `baseline=first/last` 定位基准列 key。
4. 每个普通单元格写入 `metric.name = current[of] / baseline[of]`。
5. 基准缺失、基准为 0、非数值时写 `null`。

### 3. Pipeline 接入

修改 `PivotPipeline`：

1. 增加 `baselineRatio` Guardrail：
   - `tree + baselineRatio` fail-closed。
   - `columns` 为空 fail-closed。
   - `of` 不在原生 metrics 中 fail-closed。
2. 在 `ParentShareCalculator` 之后、`ResultShaper` 之前调用 `BaselineRatioCalculator`。
3. 不允许 `baselineRatio` 参与 having/orderBy/limit。

### 4. MCP Schema / Prompt

修改 `query_model_v3_schema.json`：

1. metric object 改为 `oneOf(parentShareMetric, baselineRatioMetric)`。
2. `baselineRatioMetric` 必须：
   - required: `name/type/of/axis/baseline`
   - `type.const = "baselineRatio"`
   - `axis.const = "columns"`
   - `baseline.enum = ["first", "last"]`
   - `additionalProperties = false`
3. 继续拒绝 `expr` 和未知属性。

修改 `query_model_v3.md`：

1. 添加 `baselineRatio` 示例。
2. 明确禁止 `CELL_AT / AXIS_MEMBER / AXIS_REF`。
3. 明确第一版只支持列轴 `first/last`。

## 测试要求

### 单元测试

补充或新增：

```text
PivotMetricItemTest
BaselineRatioCalculatorTest
```

覆盖：

1. 合法 baselineRatio。
2. 缺失 baseline。
3. axis=rows。
4. baseline 非 first/last。
5. expr/path/member/index/level/parentLevel 被拒绝。
6. first / last 计算。
7. 多 row group 隔离。
8. 基准缺失、基准为 0、subtotal / grandTotal 输出 null。

### 集成与 SQL Parity

必须直接执行 Foggy Pivot 查询，并与独立 SQL oracle 逐格比对。不要只断言字段存在或结果非空。

在 `PivotSqlParityIntegrationTest` 中补齐：

1. SQLite baseline=first。
2. SQLite baseline=last。
3. `systemSlice` 或用户 `slice` 后 parity。
4. `deniedColumns` 命中 `of` 底层物理列时 fail-closed。
5. MySQL8 同一 parity。
6. PostgreSQL 同一 parity。

SQL oracle 可用 CTE + window：

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

## 建议验证命令

```powershell
mvn test -pl foggy-dataset-model "-Dtest=PivotMetricItemTest,BaselineRatioCalculatorTest,ParentShareCalculatorTest" "-P!multi-db"
mvn test -pl foggy-dataset-model "-Dtest=PivotIntegrationTest,PivotSqlParityIntegrationTest" "-Dspring.profiles.active=sqlite" "-P!multi-db"
mvn test -pl foggy-dataset-mcp "-Dtest=PivotSchemaValidationTest,AnalystMcpControllerTest" "-P!multi-db"
mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest" "-Dspring.profiles.active=docker" "-P!multi-db"
mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest" "-Dspring.profiles.active=postgres" "-P!multi-db"
```

如果 MySQL8 或 PostgreSQL 环境不可用，请明确记录原因，不要把 SQLite 通过等同于三库签收。

## 文档回写

实现完成后更新：

1. `docs/9.0.0.beta/acceptance/s12_baseline_ratio_progress.md`
2. `docs/9.0.0.beta/detailed_design/06_s11_metrics_unification_and_derived_metrics.md`
3. `docs/9.0.0.beta/detailed_design/05_cell_at_cross_axis_evaluation.md`
4. `docs/9.0.0.beta/mdx_vs_foggy_syntax_comparison.md`
5. `docs/9.0.0.beta/acceptance/version-signoff.md`
6. `docs/9.0.0.beta/README.md`

## 完成口径

最终回复请包含：

1. 修改文件列表。
2. 新增测试列表。
3. SQLite / MySQL8 / PostgreSQL 各自的测试命令与结果。
4. SQL Parity 是否为真实 Pivot 查询结果与独立 SQL oracle 的逐格比对。
5. `systemSlice`、用户 `slice`、`deniedColumns` 覆盖情况。
6. 仍然不支持的边界。
