# 9.0.0 详细设计 (06)：S11 Pivot Metrics Unification & Structured Derived Metrics

## 一、背景与定位

当前 9.0.0.beta Java Pivot Engine 已覆盖大部分主流 BI 场景：基础透视、CrossJoin、TopN、Subtotal / GrandTotal、Tree、Properties、Non-Additive Rollup、SQL Parity、权限链路都已完成签收。后续不应继续追求完整 MDX 坐标系统，而应补齐高频业务缺口，并保持 DSL 对 LLM 足够简单。

本文档定义 S11 阶段的能力取舍决策、DSL 统一方向、语义规则、已签收范围和后续推进路线。

> **阶段状态（2026-05-01）**：S11.2 `pivot.metrics` 混合数组解析与 S11.3 `parentShare` 第一版已完成 Java Core、MCP Schema、Prompt 和 SQLite/MySQL8/PostgreSQL SQL Parity 验收。`baselineRatio` 已进入 S12 执行计划，详见 `07_s12_baseline_ratio_execution_plan.md`；`expr` 对象指标未纳入第一版公开 Schema，当前由 Schema 与 runtime 双层 fail-closed。

---

## 二、能力取舍结论

| 能力 | 建议状态 | 状态码 | 结论 |
|---|---|---|---|
| `AXIS_MEMBER` | 不开放公开 DSL | `rejected-for-public-dsl` | 多层轴 index 语义天然歧义，LLM 容易误用 |
| 通用 `CELL_AT` | 不开放公开 DSL | `rejected-for-public-dsl` | 坐标漫游复杂度过高，不适合作为 LLM 可生成接口 |
| 级联 Generate | 暂缓 | `deferred / known-limitation` | 单层分组 TopN 已覆盖多数场景，级联需求频率低，成本不优先 |
| `ROLLUP_TO` 父级占比 | 已用结构化 DSL 覆盖第一版 | `implemented-as-structured-dsl(parentShare-v1)` | `parentShare` 替代公开函数字符串，第一版仅 rows 轴相邻层级 |
| 基准引用 | 进入 S12 执行计划 | `planned-as-structured-dsl(baselineRatio)` | 用结构化 `baselineRatio` 替代 `CELL_AT`，执行计划见 `07_s12_baseline_ratio_execution_plan.md` |
| `calculatedFields` | Pivot 模式弱化 | `compat-only` | 第一版不把 `expr` 放入 `pivot.metrics` 公开 Schema |

> **注意**：`AXIS_MEMBER` 和通用 `CELL_AT` 的状态不是"永久搁置"，而是 `rejected-for-public-dsl`——即不作为 LLM 可生成的公开 DSL 暴露。引擎内部是否利用等价逻辑优化执行路径属于实现细节，不影响此决策。

---

## 三、推荐 DSL 方向：Pivot Metrics 统一

### 设计原则

不要新增独立的 `derivedMetrics` 块，也尽量不要让 Pivot 模式继续依赖顶层 `calculatedFields`。推荐统一收敛到 `pivot.metrics`。

LLM 心智模型会更简单：

- `rows`：按什么分组看
- `columns`：横向展开什么
- `metrics`：输出哪些指标，包括原生度量和受控 Pivot 语义派生指标
- `options / layout`：展示与小计控制

### DSL 完整示例

```json
{
  "pivot": {
    "rows": ["product$category", "product$subCategory"],
    "columns": ["salesDate$month"],
    "metrics": [
      "salesAmount",
      {
        "name": "subCategoryShareInCategory",
        "type": "parentShare",
        "of": "salesAmount"
      },
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

### `metrics` 支持的三类元素

```json
"metrics": [
  "原生度量",
  { "name": "...", "type": "parentShare", "of": "..." }
]
```

> S11 阶段公开 Schema 只接受原生度量与 `parentShare` 两类元素。S12 已在同一结构化 `pivot.metrics` 路线下补齐 `baselineRatio`；`expr` 对象指标暂不开放，复杂算术指标继续走顶层 `calculatedFields` 的兼容路径或后续单独设计。

### 规则

1. `type=parentShare` 必须提供 `name/type/of`，Schema 禁止额外属性。
2. `of` 表示基于哪个原生度量，且第一版只允许可加度量。
3. `parentShare` 默认可从 `rows` 的相邻层级推断父级。
4. 有歧义时 fail-closed，要求补 `axis / level / parentLevel`。
5. `axis` 第一版仅允许 `rows`；显式或隐式落到 `columns` 都必须拒绝。
6. 顶层 `calculatedFields` 保留兼容，但 `pivot.metrics` 第一版不开放 `{name, expr}`。
7. 不开放 `ROLLUP_TO / CELL_AT / AXIS_MEMBER` 字符串函数。

---

## 四、`parentShare` 语义定义

### 高频场景（隐式推断）

```json
{
  "name": "subCategoryShareInCategory",
  "type": "parentShare",
  "of": "salesAmount"
}
```

当 `rows = ["product$category", "product$subCategory"]` 时，引擎可推断为：

> 当前子品类销售额 / 所属大品类销售额

### 歧义场景（显式写法）

```json
{
  "name": "share",
  "type": "parentShare",
  "of": "salesAmount",
  "axis": "rows",
  "level": "product$subCategory",
  "parentLevel": "product$category"
}
```

### 第一版限制

1. 只支持同一轴相邻层级。
2. 不支持 `hierarchyMode=tree`。
3. 不支持跨轴父级。
4. 不支持任意祖先跳转。
5. 不使用 `REMOVE(childDim)` 假装等价。
6. 不支持不可加度量；`AVG/COUNT_DISTINCT` 等必须 fail-closed。

### 执行逻辑

`parentShare` 的父级值本质上等于当前 rows 层级向父级 rollup 后的聚合值。第一版在 Phase 2.8 以内存后置计算完成：

1. 根据 `rows`、`level`、`parentLevel` 解析同轴相邻父子层级。
2. 按父级坐标构建可加度量聚合桶。
3. 计算 `childValue / parentValue`，除零或缺失返回 `null`。
4. 将结果写入当前单元格，作为输出指标进入 Phase 3。

无论哪种路径，`parentShare` 的计算结果仅作为输出指标挂载，不参与 `having / orderBy / limit`。

---

## 五、`baselineRatio` 语义定义

### 基准指数场景

```json
{
  "name": "salesIndex",
  "type": "baselineRatio",
  "of": "salesAmount",
  "axis": "columns",
  "baseline": "first"
}
```

含义：

> 当前单元格销售额 / 同一行坐标下列轴第一个成员的销售额

### 固定成员引用（第二阶段扩展）

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

### 第一版建议

1. 先支持 `baseline: "first" | "last"`。
2. 固定 member path 可以第二阶段做。
3. 不支持多层轴裸 index。
4. 不引用 subtotal / grandTotal。
5. 缺失基准、除零、null 返回 `null`。

### 执行逻辑

在 Phase 2 的 SubtotalInjector 之后、PropertyAttacher 之前，新增 `DerivedMetricCalculator` 阶段：

1. 构建坐标索引 `Map<CellCoord, Map<String, Object>>`。
2. 对每个 `baselineRatio` 类型的 metric，根据 `axis` 和 `baseline` 定位基准单元格。
3. 计算 `currentValue / baselineValue`，除零或缺失时返回 `null`。
4. 将结果写入当前单元格。

---

## 六、推进顺序

阶段命名：**S11：Pivot Metrics Unification & Structured Derived Metrics**

### S11.1 设计收口

- 更新 DSL 设计文档（本文档）。
- 明确 `pivot.metrics` 混合结构。
- 明确顶层 `calculatedFields` 在 Pivot 模式下仅兼容，不推荐。
- 明确 `ROLLUP_TO / CELL_AT / AXIS_MEMBER` 不作为公开 DSL。

### S11.2 实现 metrics 统一解析（已签收）

- `metrics` 支持字符串和对象。
- 对象第一版仅支持 `{name, type: "parentShare", of}`。
- `{name, expr}` 未纳入当前公开 Schema，必须 fail-closed。
- 校验名称冲突、依赖缺失、非法 axis、额外属性和缺失 `of`。

### S11.3 实现 `parentShare`（已签收）

- 第一版只做 rows 轴相邻层级。
- 支持可加度量。
- 不可加度量 fail-closed。
- 已补 SQLite / MySQL8 / PostgreSQL SQL Parity 与权限测试。

### S12 实现 `baselineRatio`（执行计划已拆分）

- 先支持 `first / last`。
- 内存后置计算。
- 补 SQLite / MySQL8 / PostgreSQL SQL Parity。
- 详细任务拆解、Guardrail、测试矩阵和开工提示词见 `07_s12_baseline_ratio_execution_plan.md` 与 `../acceptance/s12_baseline_ratio_antigravity_prompt.md`。

### S11.5 Prompt / Schema / 文档收口

- MCP Schema 强约束。
- Prompt 示例更新。
- MDX 对比文档更新。
- Signoff 风险项更新。

---

## 七、验收要求

每个正式支持的能力都必须满足：

1. 直接执行 Pivot 查询。
2. 与独立 SQL oracle 做真实结果比对。
3. SQLite / MySQL8 / PostgreSQL 三库覆盖。
4. `systemSlice` 权限过滤覆盖。
5. `deniedColumns` 权限拒绝覆盖。
6. MCP Schema 非法结构 fail-closed。
7. Prompt 中明确禁止生成 `ROLLUP_TO / CELL_AT / AXIS_MEMBER`。

---

## 八、总结

下一步不要直接实现 `ROLLUP_TO` 函数字符串，也不要继续推进通用 `CELL_AT`。方向统一为：

> Pivot 模式下，所有输出指标统一声明在 `pivot.metrics` 中；原生度量用字符串，受控派生指标用对象；父级占比已通过结构化 `parentShare` 覆盖，基准引用已通过 S12 结构化 `baselineRatio` 覆盖。

这条路线能补齐最显眼的业务缺口，同时保持 DSL 简单、Schema 可约束、LLM 容易生成、SQL Parity 可验收。
