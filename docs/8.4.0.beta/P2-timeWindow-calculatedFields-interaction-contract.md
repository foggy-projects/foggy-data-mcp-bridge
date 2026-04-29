# P2-timeWindow-calculatedFields 联动语义契约

## 文档作用

- doc_type: design
- intended_for: design-review / execution-agent / cross-engine-parity
- purpose: 定义 `timeWindow` 与 `calculatedFields` 在同一查询中共存时的执行顺序、引用规则、允许/禁止矩阵和错误码体系
- 目标版本: 8.4.0.beta（契约）/ 8.5.0.beta（实现）
- 需求等级: P2
- 状态: spec-ready
- 责任仓: foggy-data-mcp-bridge（Java）
- 关联 Gap: G6（`compose-query-manuals-gap-tracker.md`）

## 关联文档

- timeWindow 设计稿：`docs/8.3.0.beta/P1-SemanticDSL-时间窗口能力设计.md`（accepted）
- timeWindow 实施进度：`docs/8.3.0.beta/P1-SemanticDSL-时间窗口能力-progress.md`（accepted）
- Gap tracker：`docs/8.3.0.beta/compose-query-manuals-gap-tracker.md` G6
- S16 历史决策：timeWindow progress S16 — rolling/cumulative 从 calculatedFields 路径切换为 Compose plan 执行

## 背景

8.3.0.beta `P1-SemanticDSL-时间窗口能力设计.md` §共存规则（L249）声明：

> 同一查询中可同时使用 `calculatedFields` 和 `timeWindow`，但 `targetMetrics` 不得引用 `calculatedFields` 中定义的字段（循环依赖）。

该规则只描述了 `targetMetrics` 约束，未定义"后置 calculatedFields 引用 timeWindow 输出列"的完整语义。本文档补全该缺口。

Python 侧 v1.5 当前对此场景保持 fail-closed：`TIMEWINDOW_CALCULATED_FIELDS_NOT_IMPLEMENTED`。

---

## 代码勘察依据

### 执行管线顺序

Java 引擎 `DataSetResultStep.beforeQuery` 按 `@Order` 执行：

| Order | Step | 职责 |
|-------|------|------|
| -25 | `FieldAccessPermissionStep` | fieldAccess 白名单校验 |
| **-22** | **`TimeWindowInterceptor`** | **解析 timeWindow → QueryPlan AST，`skipQuery=true`** |
| -20 | `SyntheticMemberInternalPatchStep` | QM 合成成员 |
| default | `InlineExpressionPreprocessStep` | **calculatedFields 预编译** |
| default | `SchemaAwareFieldValidationStep` | 列引用合法性校验 |

`TimeWindowInterceptor` 先于 calculatedFields 预处理执行。timeWindow 展开后走 Compose plan 执行路径（`ctx.setSkipQuery(true)`）。

### S16 决策约束

progress S16 明确记录：rolling / cumulative 从 calculatedFields window function 路径切换为 Compose plan 执行，原因是 `SUM(metric) OVER (...)` 作为 inline calculated field 被聚合校验误判。本契约遵守此决策。

### 现有隐式行为

`TimeWindowValidator.validate()` step 9 中 `targetMetrics` 校验：`measureFields.contains(metric)` — `measureFields` 仅含 TM/QM 声明的聚合度量，不含 `request.calculatedFields.name`，因此已隐式拒绝 calc field 作为 targetMetrics。但错误码是通用的 `TARGET_NOT_AGGREGATE`，不够明确。

---

## 契约定义

### §1 执行顺序

```
1. timeWindow 展开 → base aggregation → rolling / cumulative / comparative SQL 生成
2. (新增) 后置 calculatedFields 作为外层 projection（仅 scalar row-level）
3. ORDER BY / LIMIT 位于最终层
```

timeWindow 先完成完整的 SQL 生成（包括 JOIN / WINDOW FUNCTION / CTE），后置 calculatedFields 作用于 timeWindow 最终输出列。

### §2 允许/禁止矩阵

| 场景 | 状态 | 理由 |
|------|------|------|
| `targetMetrics` 引用 `request.calculatedFields.name` | ❌ 禁止 | 循环依赖 + 设计稿 §共存规则显式声明 |
| 后置 scalar calculatedFields 引用 timeWindow 输出列 | ✅ 允许 | 核心业务场景（如 `salesAmount__ratio * 100`） |
| 后置 calculatedFields 引用维度列 | ✅ 允许 | 最终投影中的维度列可引用 |
| 后置 calculatedFields 带 `agg` 非空 | ❌ 禁止 | 二次聚合改变 timeWindow 结果语义 |
| 后置 calculatedFields 带 `windowFrame` | ❌ 禁止 | S16 决策：window function 在 timeWindow 上下文不可靠 |
| 后置 calculatedFields 带 `partitionBy` | ❌ 禁止 | 同 windowFrame |
| 后置 calculatedFields 带 `windowOrderBy` | ❌ 禁止 | 同 windowFrame |
| 对 timeWindow 派生列再次聚合 | ❌ 禁止 | 与 S16 决策一致 |
| calculatedFields 作为 targetMetrics 输入（未来阶段） | ❌ 不规划 | 循环依赖风险 + 实现复杂度不可控 |

### §3 后置 calculatedFields 可引用的列

timeWindow 最终输出中的所有列名，具体取决于 comparison 模式：

#### 同环比（yoy / mom / wow）

- 维度列：`product$category`, `salesDate$month` 等
- 时间 grain key：`salesDate$year`, `salesDate$month` 等
- 原始度量：`salesAmount`
- 前期值：`salesAmount__prior`
- 差值：`salesAmount__diff`
- 增长率：`salesAmount__ratio`

#### 滚动窗口（rolling_*）

- 维度列
- 原始度量：`salesAmount`
- 滚动值：`salesAmount__rolling_7d`, `salesAmount__rolling_30d`

#### 累计（ytd / mtd）

- 维度列
- 原始度量：`salesAmount`
- 累计值：`salesAmount__ytd`, `salesAmount__mtd`

### §4 错误码体系

| 错误码 | 触发条件 | 错误信息模板 |
|--------|---------|-------------|
| `TIMEWINDOW_TARGET_CALCULATED_FIELD_UNSUPPORTED` | `targetMetrics` 引用了 `request.calculatedFields` 定义的字段名 | `timeWindow.targetMetrics '{name}' references a calculatedField, which is not supported` |
| `TIMEWINDOW_POST_CALCULATED_FIELD_NOT_FOUND` | 后置 calc field 表达式引用了 timeWindow 输出列集中不存在的列 | `calculatedField '{name}' references '{col}' which is not available in timeWindow output` |
| `TIMEWINDOW_POST_CALCULATED_FIELD_AGG_UNSUPPORTED` | 后置 calc field 带了非空的 `agg` | `calculatedField '{name}' with agg='{agg}' is not supported in timeWindow context` |
| `TIMEWINDOW_POST_CALCULATED_FIELD_WINDOW_UNSUPPORTED` | 后置 calc field 使用了 `windowFrame / partitionBy / windowOrderBy` | `calculatedField '{name}' with window clause is not supported in timeWindow context` |

### §5 SQL 结构约束

后置 calculatedFields 必须作为 timeWindow 完整 SQL 的外层 projection：

```sql
SELECT tw_result.*,
       (salesAmount__ratio * 100) AS growthPercent,
       (salesAmount - salesAmount__rolling_7d) AS rollingGap
FROM (
    -- timeWindow 生成的完整 SQL（含 JOIN / WINDOW FUNCTION / CTE）
    ...
) tw_result
ORDER BY ... LIMIT ...
```

关键约束：
- ORDER BY / LIMIT 位于最外层，不在 timeWindow 内部 SQL 中
- 后置 calc field 不改变 timeWindow 内部 SQL 结构
- 后置 calc field 是纯 projection，不引入新的 GROUP BY / HAVING / 子查询

---

## 示例

### 正例 1：同环比 + 增长百分比

```json
{
  "columns": ["product$category", "salesDate$month", "salesAmount"],
  "timeWindow": {
    "field": "salesDate$id",
    "grain": "month",
    "comparison": "yoy",
    "value": ["2024-01-01", "2025-01-01"]
  },
  "calculatedFields": [
    {
      "name": "growthPercent",
      "expression": "salesAmount__ratio * 100"
    }
  ]
}
```

输出列：`product$category`, `salesDate$month`, `salesAmount`, `salesAmount__prior`, `salesAmount__diff`, `salesAmount__ratio`, `growthPercent`

### 正例 2：滚动 + 偏差

```json
{
  "columns": ["salesDate$id", "salesAmount"],
  "timeWindow": {
    "field": "salesDate$id",
    "grain": "day",
    "comparison": "rolling_7d",
    "value": ["-1M", "now"],
    "targetMetrics": ["salesAmount"]
  },
  "calculatedFields": [
    {
      "name": "rollingGap",
      "expression": "salesAmount - salesAmount__rolling_7d"
    }
  ]
}
```

输出列：`salesDate$id`, `salesAmount`, `salesAmount__rolling_7d`, `rollingGap`

### 反例 1：targetMetrics 引用 calculatedField

```json
{
  "columns": ["product$category", "salesAmount"],
  "timeWindow": {
    "field": "salesDate$id",
    "grain": "month",
    "comparison": "yoy",
    "value": ["2024-01-01", "2025-01-01"],
    "targetMetrics": ["unitPrice"]
  },
  "calculatedFields": [
    {
      "name": "unitPrice",
      "expression": "salesAmount / quantity"
    }
  ]
}
```

→ 错误：`TIMEWINDOW_TARGET_CALCULATED_FIELD_UNSUPPORTED`

### 反例 2：后置 calc field 使用聚合

```json
{
  "columns": ["product$category", "salesAmount"],
  "timeWindow": {
    "field": "salesDate$id",
    "grain": "month",
    "comparison": "yoy",
    "value": ["2024-01-01", "2025-01-01"]
  },
  "calculatedFields": [
    {
      "name": "avgRatio",
      "expression": "salesAmount__ratio",
      "agg": "AVG"
    }
  ]
}
```

→ 错误：`TIMEWINDOW_POST_CALCULATED_FIELD_AGG_UNSUPPORTED`

### 反例 3：后置 calc field 使用窗口函数

```json
{
  "columns": ["product$category", "salesAmount"],
  "timeWindow": {
    "field": "salesDate$id",
    "grain": "month",
    "comparison": "yoy",
    "value": ["2024-01-01", "2025-01-01"]
  },
  "calculatedFields": [
    {
      "name": "rankByRatio",
      "expression": "RANK()",
      "partitionBy": ["product$category"],
      "windowOrderBy": [{"field": "salesAmount__ratio", "dir": "desc"}]
    }
  ]
}
```

→ 错误：`TIMEWINDOW_POST_CALCULATED_FIELD_WINDOW_UNSUPPORTED`

---

## Python 对齐指引

### 错误码映射

Python 侧当前使用 `TIMEWINDOW_CALCULATED_FIELDS_NOT_IMPLEMENTED` 全局拒绝。升级至本契约后，应替换为 4 个细化错误码：

| Java 错误码 | Python 对应 |
|-------------|------------|
| `TIMEWINDOW_TARGET_CALCULATED_FIELD_UNSUPPORTED` | 同名 |
| `TIMEWINDOW_POST_CALCULATED_FIELD_NOT_FOUND` | 同名 |
| `TIMEWINDOW_POST_CALCULATED_FIELD_AGG_UNSUPPORTED` | 同名 |
| `TIMEWINDOW_POST_CALCULATED_FIELD_WINDOW_UNSUPPORTED` | 同名 |

### Parity Fixture 路径

Java 侧新增 fixture 落盘于 `foggy-dataset-model/src/test/resources/parity/timeWindow/`，Python 侧应镜像。

### 实现阶段建议

1. 第一步：将 `TIMEWINDOW_CALCULATED_FIELDS_NOT_IMPLEMENTED` 细化为 4 个错误码
2. 第二步：实现后置 scalar calculatedFields 外层 projection
3. 第三步：镜像 parity fixture 并验证

---

## 验收标准

1. 本契约文档通过审核并落盘
2. G6 gap tracker 状态更新为 `spec-ready`
3. 错误码体系在 Java 侧实现（8.5.0）
4. 正反例 parity fixture 落盘
5. 现有 timeWindow 11 个 fixture 不回归
6. 现有 calculatedFields 测试不回归
