# P1-SemanticDSL-时间窗口能力设计

## 文档作用

- doc_type: design
- intended_for: design-review / execution-agent
- purpose: 定义面向 AI 的声明式 Single Query DSL `timeWindow` 结构，作为上游 P1 时间分析能力增强的高层 DSL 包装层
- 目标版本: 8.3.0.beta
- 需求等级: P1（随上游 P1-ComposeQuery-时间分析能力增强）
- 状态: draft
- 责任仓: foggy-data-mcp-bridge（Java）+ foggy-odoo-bridge-pro（Python）
- 责任模块: foggy-dataset-model / foggy-dataset-mcp

## 关联文档

- 上游需求：[P1-ComposeQuery-时间分析能力增强-需求](../8.2.0.beta/P1-ComposeQuery-时间分析能力增强-需求.md)
- 上游评估：[P1-ComposeQuery-时间分析能力增强评估](../8.2.0.beta/P1-ComposeQuery-时间分析能力增强评估.md)
- 上游进度：[P1-ComposeQuery-时间分析能力增强-progress](../8.2.0.beta/P1-ComposeQuery-时间分析能力增强-progress.md)
- 元数据增强：[P2-Metadata时间维度与属性分析报告](./P2-Metadata时间维度与属性分析报告.md)
- 模型发现元数据评估：[P2-get_metadata模型发现返回样例与瘦身评估](./P2-get_metadata模型发现返回样例与瘦身评估.md)
- 模型发现入口切换：[P2-list_models模型发现入口与get_metadata隐藏-需求](./P2-list_models模型发现入口与get_metadata隐藏-需求.md)
- 参考手册基线：[P0-ComposeQuery-CTE使用参考手册](../8.2.0.beta/P0-ComposeQuery-CTE使用参考手册.md)

## 架构定位

本文档定义的 `timeWindow` 是上游 P1 §推荐设计方向 §[NOTE] 中提出的 **Single Query DSL 包装层**。它与底层 QueryPlan 窗口主语义的关系为：

```
AI / 分析师
    ↓ 填写 timeWindow JSON
SemanticDSL 解析器（本文档定义）
    ↓ 解析 + 展开
QueryPlan 主语义层（上游 P1 定义的 .lag().over() 等 API）
    ↓ 编译
SQL（窗口函数 / CTE JOIN / 方言表达式）
```

**约束**：本能力**必须在底层 QueryPlan 窗口 / 同环比主语义落地之后**才能实现。本文档仅定义 DSL 层面的 API 契约与解析行为。

---

## 前提与依赖

| 依赖项 | 说明 | 状态 |
|--------|------|------|
| P2 §建议1：DbDimension 增加 timeRole | AI 需要通过 `business_date` 标识选择正确的时间轴字段 | `done`（Java/Python 已实现） |
| P2 §建议2：演示 TM 补 timeRole 配置 | 演示模板中 `salesDate` 维度需显式配置 `timeRole: 'business_date'` | `pending` |
| P2：时间维度样例行 | AI 需要通过真实样例值理解 `salesDate$id`、`caption`、年月日等字段值形态 | `draft`（见 `P2-Metadata时间维度样例行-需求.md`） |
| P2：模型发现入口切换 | AI 首轮应通过 `dataset.list_models` 发现模型，避免 `get_metadata` 全量索引膨胀 | `approved`（见 `P2-list_models模型发现入口与get_metadata隐藏-需求.md`） |
| 上游 P1 §1：窗口函数进入 QueryPlan 主语义 | `OverClause` 已有骨架，需完成 lag/lead/running 等完整语义 | `approved` |
| 上游 P1 §2：时间偏移与同比环比主语义 | 底层需支持 period-shift + JOIN 生成 | `approved` |
| 上游 P1 §3：区间累计（MTD/YTD/rolling） | 底层需支持累计窗口语义 | `approved` |
| 阶段切断规则冻结 | `.select()` 创建新阶段，新阶段只看当前投影列 | `assumed-ready` |

---

## 非目标

本批次明确排除以下能力：

- 多窗口对比数组（本批仅支持单个 `timeWindow`）
- 自定义会计日历 / 财年期间
- 任意 N 期偏移（本批仅支持 1 期偏移）
- 时区切换 / 跨时区对比
- 连续时间轴补齐（属上游 P1 第四层，本文档不涵盖）
- 复杂 cohort / retention 全家桶 DSL
- 完整 SQL 时间函数映射

---

## DSL 语法定义

在现有 `SemanticQueryRequest` 中增加 `timeWindow` 属性。

```json
{
  "columns": ["product$category", "salesAmount"],
  "slice": [],
  "timeWindow": {
    "field": "salesDate$id",
    "grain": "month",
    "comparison": "yoy",
    "range": "[)",
    "value": ["2024-01-01", "2025-01-01"],
    "targetMetrics": ["salesAmount"]
  }
}
```

> **向后兼容占位**：未来扩展为多窗口时，将使用 `timeWindows: [...]` 数组字段。本批仅支持 `timeWindow`（单数），`timeWindows` 保留但不解析，长度限制为 1。

### 参数详解

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `field` | String | 是 | 时间轴字段。**必须**选择元数据中 `timeRole=business_date` 的字段。维度用 `dim$id`，普通时间属性用裸名。 |
| `grain` | String | 是 | 时间对齐粒度。枚举：`day`, `week`, `month`, `quarter`, `year` |
| `comparison` | String | 是 | 窗口计算模式。详见下方兼容矩阵。 |
| `range` | String | 否 | 范围开闭。仅允许 `[)`（默认, 左闭右开）和 `[]`（全闭）。其它值报错。 |
| `value` | String[2] | 是 | 用户关注的数据区间（current period）。同环比场景下 planner 自动推导 prior 区间，LLM 无需关心。支持绝对日期和相对表达式（见下方文法）。 |
| `targetMetrics` | String[] | 否 | 指定应用窗口的度量字段。留空则作用于 columns 中所有 Measure。 |

### `field` 命名规则

| 时间来源类型 | field 值格式 | 示例 |
|-------------|-------------|------|
| 星型时间维度（DimensionJoin） | `{dimName}$id` | `salesDate$id` |
| 扁平日期属性（Date/Datetime列） | 裸字段名 | `orderDate` |

AI 生成时必须参照 `qm_describe.md` 中 `## 时间维度与字段` 区域标记为 `business_date` 的字段。

### `value` 相对表达式文法

```
relative_expr ::= sign offset_unit | "now"
sign          ::= "-" | "+"
offset_unit   ::= integer unit_char
unit_char     ::= "D" | "W" | "M" | "Q" | "Y"
```

语义规则：
- `now` → 当前日期（`CURRENT_DATE`），精确到日，不含时分秒
- `-1Y` → `CURRENT_DATE - INTERVAL '1' YEAR`（日历年偏移，非会计年）
- `-7D` → `CURRENT_DATE - INTERVAL '7' DAY`
- `-1M` → `CURRENT_DATE - INTERVAL '1' MONTH`
- `-1Q` → `CURRENT_DATE - INTERVAL '3' MONTH`（季度 = 3 个月）
- 时区：统一使用数据库服务器时区，本批不支持时区切换

四方言 lowering 示例（`-1Y` → 绝对日期）：

| 方言 | SQL 表达式 |
|------|-----------|
| MySQL | `DATE_SUB(CURDATE(), INTERVAL 1 YEAR)` |
| PostgreSQL | `CURRENT_DATE - INTERVAL '1 year'` |
| SQL Server | `DATEADD(YEAR, -1, CAST(GETDATE() AS DATE))` |
| SQLite | `DATE('now', '-1 year')` |

### Comparison 模式与 Grain 兼容矩阵

#### 模式列表

| comparison | 类型 | 说明 |
|-----------|------|------|
| `yoy` | 同比 | Year-over-Year，与上一年同期对比 |
| `mom` | 环比 | Month-over-Month，与上一月对比 |
| `wow` | 周环比 | Week-over-Week，与上一周对比 |
| `ytd` | 年累计 | Year-to-Date，年初至今 |
| `mtd` | 月累计 | Month-to-Date，月初至今 |
| `rolling_7d` | 滚动7天 | 过去7天滚动窗口 |
| `rolling_30d` | 滚动30天 | 过去30天滚动窗口 |
| `rolling_90d` | 滚动90天 | 过去90天滚动窗口 |

> 上游 P1 评估中的 `qtd`（季度累计）和 `wtd`（周累计）**有意裁剪**至后续批次，理由是使用频率低于 ytd/mtd，且底层偏移逻辑更复杂。

#### 兼容矩阵

| comparison＼grain | day | week | month | quarter | year |
|---|---|---|---|---|---|
| `yoy` | ❌ `GRAIN_INCOMPATIBLE` | ✅ | ✅ | ✅ | ✅ |
| `mom` | ❌ `GRAIN_INCOMPATIBLE` | ❌ | ✅ | ❌ | ❌ |
| `wow` | ✅ | ✅ | ❌ `GRAIN_INCOMPATIBLE` | ❌ | ❌ |
| `ytd` | ✅ | ✅ | ✅ | ✅ | ❌ |
| `mtd` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `rolling_7d` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `rolling_30d` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `rolling_90d` | ✅ | ✅ | ❌ | ❌ | ❌ |

不兼容组合由引擎返回 `TIMEWINDOW_GRAIN_INCOMPATIBLE` 错误码。

---

## 输出列后缀规约

引擎在处理 `timeWindow` 后，自动追加派生列。命名规约冻结如下：

### 同环比类（yoy/mom/wow）

| 后缀 | 含义 | 示例 |
|------|------|------|
| `{metric}` | 当期值（原列，不变） | `salesAmount` |
| `{metric}__prior` | 前期值 | `salesAmount__prior` |
| `{metric}__diff` | 差值（当期 - 前期） | `salesAmount__diff` |
| `{metric}__ratio` | 增长率（(当期 - 前期) / 前期） | `salesAmount__ratio` |

`__ratio` 除零策略：当 `prior = 0` 或 `prior = NULL` 时，`ratio` 输出为 `NULL`。

### 累计类（ytd/mtd）

| 后缀 | 含义 | 示例 |
|------|------|------|
| `{metric}__ytd` | 年初至今累计 | `salesAmount__ytd` |
| `{metric}__mtd` | 月初至今累计 | `salesAmount__mtd` |

### 滚动类（rolling_*）

| 后缀 | 含义 | 示例 |
|------|------|------|
| `{metric}__rolling_{N}{unit}` | 滚动窗口值 | `salesAmount__rolling_7d` |

滚动聚合函数：默认**继承度量原本的聚合类型**。即 SUM 度量生成滚动 SUM，AVG 度量生成滚动 AVG。如需覆盖，通过可选参数 `rollingAggregator: 'sum' | 'avg' | 'count'` 指定。

### 与 columns 闭包契约的关系

`timeWindow` 自动追加的派生列**不需要**在 `columns` 中显式声明。引擎在 SemanticServiceV3 层通过 `derivedColumns` 通道将它们拼接到最终投影列表中。这些派生列在当前阶段生成后，**可以**在后续 `.select()` 阶段被引用（遵守阶段切断规则）。

---

## 与底层 QueryPlan 的映射

### 场景 A：同环比（yoy/mom/wow）→ DerivedQueryPlan + JoinPlan

**不**走字符串模板路径。严格映射到既有 QueryPlan SPI：

1. **基准 Stage**：构建 `QueryPlan`，应用 `value` 区间作为 slice，按 `grain` 对应的时间字段 + 维度字段进行 Group By。
2. **偏移 Stage**：克隆基准 Stage，通过 `DerivedQueryPlan` 将时间切片按 `comparison` 语义偏移（yoy→-1Y, mom→-1M, wow→-1W）。
3. **合并 Stage**：通过 `JoinPlan`（LEFT JOIN）以**所有维度字段** + 对齐后的时间粒度字段为 ON 条件，合并基准与偏移数据。JOIN ON 必须覆盖全部维度列，不能只 join grain key，否则不同客户、地区、产品等维度会串行。
4. **投影 Stage**：`.select()` 输出当期值、`__prior`、`__diff`（当期-前期）、`__ratio`（(当期-前期)/前期）四列。

每个 Stage 严格遵守阶段切断规则，新 Stage 只能看到当前投影列。

### 场景 B：滚动窗口（rolling_*）→ OverClause

映射到 `QueryPlan` 的 `overClauses`：

1. 在 `AggregateColumn` 上注入 `OverClause`，`orderBy` 指向 `field` 对应的时间字段。
2. Window Frame：`rolling_7d` → `ROWS BETWEEN 6 PRECEDING AND CURRENT ROW`，`rolling_30d` → `ROWS BETWEEN 29 PRECEDING AND CURRENT ROW`，以此类推。
3. 聚合函数由 `rollingAggregator` 决定（默认继承度量原始聚合）。

> **语义边界**：第一批 rolling 为 **observed bucket rolling**——按实际输出桶行数滚动。如果某些日期没有数据，窗口不补齐空桶。calendar-day rolling 依赖连续时间轴补齐，放到后续 T4。

### 场景 C：累计（ytd/mtd）→ OverClause + 隐式 slice

1. 引擎根据 `comparison` 自动注入额外的时间切片（ytd：年初 → 当前行时间；mtd：月初 → 当前行时间）。
2. 通过 `OverClause` 的 `orderBy` + `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW` 实现累计。

---

## 与 calculatedFields / windowFrame（QM 既有）的关系

QM 中已存在 `ma7` 等通过 `partitionBy / windowOrderBy / windowFrame` 定义的窗口计算字段。本 `timeWindow` 与之的关系为：

| 维度 | calculatedFields（既有） | timeWindow（本文档） |
|------|------------------------|---------------------|
| 定义位置 | QM 模板中预定义 | 查询请求中动态声明 |
| 灵活度 | 高（任意窗口表达式） | 受限（标准化模式） |
| AI 友好度 | 低（需理解 OVER 语法） | 高（填配置即可） |
| 适用场景 | 非标准、复杂窗口需求 | 标准同环比/滚动/累计 |

**共存规则**：同一查询中可同时使用 `calculatedFields` 和 `timeWindow`，但 `targetMetrics` 不得引用 `calculatedFields` 中定义的字段（循环依赖）。

---

## AI 生成指引

### 何时使用 timeWindow

遇到以下用户意图时，**必须优先使用 `timeWindow`**：
- "同比"、"环比"、"YoY"、"MoM"
- "年初至今"、"月累计"、"YTD"、"MTD"
- "滚动平均"、"移动总和"、"过去N天趋势"

### 何时**不**使用 timeWindow（退回 calculatedFields）

- 非标准窗口需求（如"过去3笔订单的平均金额"——按行数而非时间粒度）
- 需要自定义 PARTITION BY 逻辑（如"每个客户的累计消费排名"）
- 需要 lag/lead 任意偏移量（如"前3期"）
- 涉及多个不同时间轴的交叉对比

### field 选择规则

1. 查找 `qm_describe.md` 中 `## 时间维度与字段` 区域
2. 选取 `timeRole = business_date` 的字段
3. 如为维度，使用 `{dimName}$id`；如为普通列，使用裸名
4. **绝对禁止**使用 `system_time` 字段（如 `created_at`）作为业务时间轴

### columns 声明规则

- `columns` 中声明维度字段和度量字段即可
- **无需**声明 `__prior` / `__diff` / `__ratio` / `__rolling_*` 等派生列
- 如 `grain=month` 且 `columns` 中未包含对应月度字段，引擎自动注入

---

## 错误码

| 错误码 | 触发条件 | 错误信息模板 |
|--------|---------|-------------|
| `TIMEWINDOW_FIELD_NOT_FOUND` | `field` 不存在于模型字段中 | `timeWindow.field '{field}' not found in model '{model}'` |
| `TIMEWINDOW_FIELD_NOT_TIME` | `field` 存在但不是时间类型/无 business_date 角色 | `timeWindow.field '{field}' is not a time field (timeRole=business_date required)` |
| `TIMEWINDOW_GRAIN_INCOMPATIBLE` | comparison × grain 组合不兼容（见矩阵） | `timeWindow grain '{grain}' is incompatible with comparison '{comparison}'` |
| `TIMEWINDOW_VALUE_PARSE_FAILED` | value 中的日期/相对表达式无法解析 | `timeWindow.value '{expr}' cannot be parsed` |
| `TIMEWINDOW_TARGET_NOT_AGGREGATE` | targetMetrics 指向非聚合字段 | `timeWindow.targetMetrics '{field}' is not an aggregate measure` |
| `TIMEWINDOW_RANGE_INVALID` | range 值不在 `[)` / `[]` 之内 | `timeWindow.range '{range}' is not supported (use '[)' or '[]')` |

---

## 示例

### 示例 1：月度同比（YoY）

用户："看下2024年各品类的月度销售额同比情况"

```json
{
  "columns": ["product$category", "salesDate$month", "salesAmount"],
  "timeWindow": {
    "field": "salesDate$id",
    "grain": "month",
    "comparison": "yoy",
    "value": ["2024-01-01", "2025-01-01"]
  }
}
```

预期输出列：

| 列名 | 来源 |
|------|------|
| `product$category` | columns 原始 |
| `salesDate$month` | columns 原始 |
| `salesAmount` | 当期值 |
| `salesAmount__prior` | 去年同月值 |
| `salesAmount__diff` | 当期 - 去年同月 |
| `salesAmount__ratio` | 增长率 |

### 示例 2：7日滚动总和

用户："按天查看过去一个月的日订单金额以及7天滑动总和"

```json
{
  "columns": ["orderDate", "orderAmount"],
  "timeWindow": {
    "field": "orderDate",
    "grain": "day",
    "comparison": "rolling_7d",
    "value": ["-1M", "now"],
    "targetMetrics": ["orderAmount"],
    "rollingAggregator": "sum"
  }
}
```

预期输出列：`orderDate`, `orderAmount`, `orderAmount__rolling_7d`

---

## 跨端实现策略

### 双仓对齐

| 仓库 | 实现路径 | 说明 |
|------|---------|------|
| foggy-data-mcp-bridge（Java） | `foggy-dataset-model` → `engine/compose/plan/` 包 | DSL 解析 → QueryPlan AST 构建 → SQL 编译 |
| foggy-odoo-bridge-pro（Python） | `foggy_mcp_pro/lib/foggy/dataset_model/semantic/` | DSL 解析 → SqlQueryBuilder 窗口/JOIN 生成 |

### Parity 策略

- 扩展 `src/test/resources/parity/` 共享测试 catalog，增加 `timeWindow/` 目录
- 每个 comparison 模式至少 1 个 parity 用例（JSON input → expected SQL → expected columns）
- 四方言 SQL parity（MySQL / PostgreSQL / SQL Server / SQLite）延续 8.2.0.beta 基线

---

## 验收标准

1. 引擎能正确解析 `timeWindow` JSON 并映射到 QueryPlan 主语义（不走 SQL 字符串模板）
2. 自动追加的派生列严格遵守后缀规约（`__prior` / `__diff` / `__ratio` / `__rolling_*` / `__ytd` / `__mtd`）
3. 派生列可在后续 `.select()` 阶段被引用（阶段切断规则不被破坏）
4. `comparison × grain` 不兼容组合返回明确错误码
5. 相对表达式（`-1Y`, `now` 等）在四方言下正确 lowering
6. AI 在 `qm_describe.md` 指引下能稳定生成 timeWindow 配置

## 测试规划

| 测试类别 | 用例数 | 说明 |
|---------|--------|------|
| 同环比 happy path | ≥3 | yoy/mom/wow 各 1 |
| 累计 happy path | ≥2 | ytd/mtd 各 1 |
| 滚动 happy path | ≥2 | rolling_7d/rolling_30d 各 1 |
| 兼容矩阵 negative | ≥4 | 覆盖 ❌ 组合的错误码触发 |
| 相对表达式解析 | ≥3 | `-1Y`, `-7D`, `now` |
| 四方言 SQL parity | ≥4 | 每种方言至少 1 个完整 timeWindow 用例 |
| 跨端 parity | ≥2 | Java/Python 同 input 同 output |
| ratio 除零 | 1 | prior=0 或 NULL 时 ratio=NULL |
