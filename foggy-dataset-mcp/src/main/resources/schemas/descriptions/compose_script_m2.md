# dataset.compose_script (SemanticDSL)

通过受控 FSScript 沙箱编排多个查询计划。只在单个 `dataset.query_model` 不能表达时使用。

## 快速路由

| 场景 | 是否使用 | 退化/边界 |
|---|---|---|
| 跨模型 Join / Union | 使用 | 先分别 `dsl({...})` 建 plan，再 `.join()` / `.union()` |
| 基于上一阶段聚合结果继续过滤、排序、分页 | 使用 | `prevPlan.query({...})`；只能引用前一阶段输出列 |
| timeWindow + 派生/Join/Union/多 plan | 使用 | 单模型同比、环比、YTD、MTD、rolling 改用 `dataset.query_model.payload.timeWindow` |
| 一次返回多个独立结果 | 使用 | `return { plans: { a, b } }` 或等价 envelope |
| 单模型过滤、分组、聚合、calculatedFields、pivot | 不使用 | 调 `dataset.query_model` |
| 单模型交叉表、小计/总计、树形 rows、parentShare、baselineRatio | 不使用 | 调 `dataset.query_model.payload.pivot`；超出边界时返回基础指标或说明不支持，不要手写 Pivot |

完整单模型 DSL 语法以 `dataset.query_model` / `query_model_v3` schema 为准；本工具只补多 plan 编排。

## 可生成入口

| 入口 | 用法 |
|---|---|
| `dsl({...})` | `model` 为 QM 名称时开始基础查询；为已有 plan 时开始派生查询 |
| `plan.query({...})` | 对已有 plan 继续投影、过滤、排序、分页 |
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
const top = sales.query({
  slice: [{ field: "totalAmount", op: ">", value: 50000 }],
  columns: ["customer$id", "totalAmount"],
  orderBy: ["-totalAmount"],
  limit: 20
});
return { plans: top };
```

基础查询常用字段：`model`、`columns`、`slice`、`having`、`groupBy`、`orderBy`、`limit`、`start`、`distinct`、`calculatedFields`、`timeWindow`。基础字段语法与 `dataset.query_model` 一致。`model` 接收查询模型名字符串或已有 QueryPlan（含派生、Join、Union）；后者等价于 `previousPlan.query({...})`。旧 `dsl({ source: previousPlan, ... })` 保留兼容；两个非空来源不能同时指定。

派生查询只引用前一阶段输出列，支持在 `columns` 中写表达式，如 `bill_amount * 2 as double_amount`。派生层不支持非空 `calculatedFields` 或 `having`，会返回参数路径和替代建议；`null` 或 `[]` 为无操作。对新别名筛选应再加一层 `.query({slice: [...]})`。不要把基础 QM 的完整计算字段能力推断为派生层能力。

基础 `dsl({...})` 的 `slice` 过滤输入明细行，语义对应 WHERE；即使字段是 QM 聚合 measure，放在 `slice` 中也保持行级语义，不会自动转成 HAVING。分组后的聚合阈值必须显式放入 DSL 对象的顶层 `having`，例如 `having: [{"field":"arOverdueAmount","op":">","value":0}]`。不要在同一个 `$or` / `$and` 逻辑组内混合行级条件和聚合后条件；需要对 Join/Union/上一阶段输出继续过滤时，使用聚合后的 plan `.query({ slice: [...] })`。

## Join / Union

Join 前先聚合事实侧，避免 1:N 明细放大：
```fsscript
const customers = dsl({
  model: "CustomerQM",
  columns: ["id AS customer_id", "name AS customer_name"]
});
const orders = dsl({
  model: "OrderQM",
  columns: ["customerId AS order_customer_id", "SUM(amount) AS total_amount"],
  groupBy: ["customerId"]
});
const joined = customers.join(orders, "left", [
  { left: "customer_id", op: "=", right: "order_customer_id" }
]);
const result = joined.query({
  columns: ["customer_id", "customer_name", "total_amount"],
  orderBy: ["-total_amount"],
  limit: 20
});
return { plans: result };
```

支持的 Join 类型：`"inner"`、`"left"`、`"right"`、`"full"`（取决于方言）。Join 条件是 AND-only 数组，只能引用左右 plan 可见字段；同名列先在源 plan 中重命名。不需要二次投影、排序或分页时，直接 `return { plans: joined };`。

Join 两侧的输出列名必须避免重复；右侧 join key 请使用不同别名（如 `order_customer_id`），不要让两个 plan 都输出 `customer_id`。凡是 Join 后还要在 `join(on)`、`.query({ columns })`、`.query({ orderBy })` 中引用的字段，都必须在源 plan 的 `columns` 中先显式起别名；不要在 Join 后继续引用 `partner$id`、`partner$caption` 这类原始字段名。所有临时别名，包括聚合别名和排序指标，都使用小写 `snake_case`，避免大小写敏感别名在 SQL 方言中被折叠。

如果 Join / 派生查询成功返回空数组（如 `plans: []`），这表示按当前业务口径没有匹配记录，是有效结果；直接回答“无匹配记录”。不要自动放宽状态、日期、公司或付款条件重新探查，除非用户明确要求排查数据质量或改口径。

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
- Do not use `.execute()` directly unless the user explicitly asks for raw execution.
- 不要手写 raw SQL 或 CTE（如 `WITH ...`），用 `dsl()` / `.join()` / `.union()`。
- 不要在脚本中传入用户身份、systemSlice、拒绝列、datasource routing 等 host-controlled security 参数。
- 派生查询的 `calculatedFields` 不要使用聚合函数或窗口字段（`partitionBy`、`windowOrderBy`、`windowFrame`）。
- `dsl({ model: ... })` 的 `model` 必须是非空字符串；对已有 plan 使用 `.query({...})` 或 `dsl({ source: plan, ... })`。
- 字段或主时间轴不确定时，先用 `dataset.list_models` 和 `dataset.describe_model_internal`。

### Compose 输入与治理边界

- `query` 只接受一个选项对象，来源是接收它的计划；来源选择用 `dsl({model: plan})` 或兼容的 `source: plan`。
- 已知数组、布尔及分页参数严格校验。分页值必须是 0..2147483647 的整数，null 等同省略，limit=0 为空结果；不能用字符串数字。未知扩展键不会因此被全局禁止。
- 基础模型 calculatedFields 保留 partitionBy、windowOrderBy、windowFrame 和 emptyDefault，并检查权限依赖；派生层非空 calculatedFields/having 仍不支持。
- 限定字段必须由该计划实际产生并沿投影可见；UNION 只暴露合并输出，不能引用分支字段。输出 alias 不得仅大小写不同。
- JOIN 条件可指定 `nullSafe: true`，仅用于 `op: '='`；默认 false，NULL 不匹配，true 时双方 NULL 匹配。参数有值必须为 boolean。
- 若部署含脱敏、结果过滤等非恒等处理，或扩展尚未明确声明 Compose 安全能力，规划会以 COMPOSE_RESULT_GOVERNANCE_UNPROVEN 拒绝。改用普通受治理查询；不要删除权限或处理器绕过拒绝。validate/preview 不执行业务数据查询。
