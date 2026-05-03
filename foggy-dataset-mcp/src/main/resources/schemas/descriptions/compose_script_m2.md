# dataset.compose_script (SemanticDSL)

通过受控 FSScript 沙箱编排多个查询计划。只在单个 `dataset.query_model` 不能表达时使用。

## 快速路由

| 场景 | 是否使用 | 退化/边界 |
|---|---|---|
| 跨模型 Join / Union | 使用 | 先分别 `dsl({...})` 建 plan，再 `.join()` / `.union()` |
| 基于上一阶段聚合结果继续过滤、排序、分页 | 使用 | `dsl({ model: prevPlan, ... })`，只能引用前一阶段输出列 |
| timeWindow + 派生/Join/Union/多 plan | 使用 | 单模型同比、环比、YTD、MTD、rolling 改用 `dataset.query_model.payload.timeWindow` |
| 一次返回多个独立结果 | 使用 | `return { plans: { a, b } }` 或等价 envelope |
| 单模型过滤、分组、聚合、calculatedFields、pivot | 不使用 | 调 `dataset.query_model` |
| 单模型交叉表、小计/总计、树形 rows、parentShare、baselineRatio | 不使用 | 调 `dataset.query_model.payload.pivot`；超出边界时返回基础指标或说明不支持，不要手写 Pivot |

完整单模型 DSL 语法以 `dataset.query_model` / `query_model_v3` schema 为准；本工具只补多 plan 编排。

## 可生成入口

| 入口 | 用法 |
|---|---|
| `dsl({...})` | 基础查询或派生查询 |
| `.join(other, type, on)` | 横向连接 plan |
| `.union(other, options)` | 纵向合并同结构 plan |

## 查询骨架

基础查询：
```fsscript
const sales = dsl({
  model: "SalesQM",
  columns: ["customer$id", "SUM(amount) AS totalAmount"],
  slice: [{ field: "state", op: "=", value: "done" }],
  groupBy: ["customer$id"]
});
return { plans: sales };
```

派生查询：
```fsscript
const top = dsl({
  model: sales,
  slice: [{ field: "totalAmount", op: ">", value: 50000 }],
  columns: ["customer$id", "totalAmount"],
  orderBy: ["-totalAmount"],
  limit: 20
});
return { plans: top };
```

常用字段：`model`、`columns`、`slice`、`groupBy`、`orderBy`、`limit`、`start`、`distinct`、`calculatedFields`、`timeWindow`。字段语法与 `dataset.query_model` 一致。

## Join / Union

Join 前先聚合事实侧，避免 1:N 明细放大：
```fsscript
const customers = dsl({ model: "CustomerQM", columns: ["id", "name AS customerName"] });
const orders = dsl({
  model: "OrderQM",
  columns: ["customerId", "SUM(amount) AS totalAmount"],
  groupBy: ["customerId"]
});
const joined = customers.join(orders, "left", [{ left: "id", op: "=", right: "customerId" }]);
return { plans: dsl({ model: joined, columns: ["id", "customerName", "totalAmount"] }) };
```

支持的 Join 类型：`"inner"`、`"left"`、`"right"`、`"full"`（取决于方言）。Join 条件是 AND-only 数组，只能引用左右 plan 可见字段；同名列先在源 plan 中重命名。

Union 只用于两侧列结构兼容、业务含义一致的 plan：
```fsscript
return { plans: online.union(offline, { all: true }) };
```
Union 后使用左侧 schema。

## timeWindow 组合

单模型时间窗口用 `dataset.query_model.payload.timeWindow`。只有当时间窗口结果还要参与派生查询、Join/Union 或多 plan 返回时，才在 `compose_script` 中使用同名 `timeWindow` 字段。

生成前先调用 `dataset.describe_model_internal`，使用标记 `timeRole=business_date` 的维度 `$id` 作为 `timeWindow.field`，如 `salesDate$id`；不要猜测时间字段，不要默认用 `created_at`、`updated_at`、`write_date`。

```fsscript
const yoy = dsl({
  model: "SalesQM",
  columns: ["salesDate$year", "salesAmount", "salesAmount__ratio"],
  groupBy: ["salesDate$year"],
  timeWindow: {
    field: "salesDate$id",
    grain: "year",
    comparison: "yoy",
    targetMetrics: ["salesAmount"]
  }
});
return { plans: yoy };
```

`timeWindow` 的 `grain`、`comparison`、`value`、`rollingAggregator`、派生列命名和 calculatedFields 边界与 `dataset.query_model` 保持一致。`targetMetrics` 必须指向当前阶段输出指标，不要指向 calculatedFields。

## 执行边界

- 返回必须是 envelope：`return { plans: yourPlan };`
- 用 `return` 输出最终值；不支持 ES module `export`。
- 除非用户明确要求，不要直接 `.execute()`。
- 不要手写 SQL 或 CTE（如 `WITH ...`），用 `dsl()` / `.join()` / `.union()`。
- 不要在脚本中传入用户身份、系统 slice、拒绝列、数据源路由等宿主安全参数。
- 派生查询的 `calculatedFields` 不要使用聚合函数或窗口字段（`partitionBy`、`windowOrderBy`、`windowFrame`）。
- 字段或主时间轴不确定时，先用 `dataset.list_models` 和 `dataset.describe_model_internal`。
