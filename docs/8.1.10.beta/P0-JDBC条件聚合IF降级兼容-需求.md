# P0 - JDBC 条件聚合 IF 降级兼容

> **状态：superseded**（2026-04-19）
> **继承方案**：`D:/foggy-projects/foggy-data-mcp/docs/v1.4/REQ-FORMULA-EXTEND-non-aggregation-functions-需求.md`
> **关闭原因**：REQ-FORMULA-EXTEND 在 v1.4 做 formula 引擎双端（Java/Python）对齐，本 REQ 的 `IF → CASE WHEN` lowering 是其 Java 子任务。本 REQ Java 侧实装已基本完成（progress.md Step 1-5 均标记"已完成"，83 tests pass），**仅剩多方言（MySQL/PG/MSSQL）验证**，该验证已吸纳到 REQ-FORMULA-EXTEND 的验收范围。
> **保留该文档**：作为历史上下文和 Java 侧实施细节的技术档案，不删除。后续讨论与进度跟踪统一在 REQ-FORMULA-EXTEND。

---

## 文档作用

- doc_type: `requirement`
- intended_for: `engine-owner | execution-agent | reviewer`
- purpose: 明确 JDBC 表达式引擎对 `聚合函数包裹 IF(...)` 的跨方言兼容需求，支撑高频条件聚合分析场景

## 基本信息

- 目标版本：`8.1.10.beta`
- 需求等级：`P0`
- 状态：`draft`
- 责任项目：`foggy-data-mcp-bridge`

## 背景

Odoo v1.3 真实 `chat/showcase` 回归中，LLM 高频生成“条件聚合”诉求，例如：

- 最近 30 天，按销售团队统计线索数、赢单数、销售订单金额，并按赢单转化率倒序

当前 planner / LLM 容易产出 SQL 风格内联条件聚合：

```text
sum(case when stage$caption = 'Won' then 1 else 0 end) as wonCount
```

但现有 DSL / 表达式链路并不接受这类原生 SQL 片段，查询会在表达式解析阶段失败。

结合引擎现状，当前最小可落地的改进方向不是一次性开放 `CASE WHEN` 语法，也不是立即升级到正式的 `count_if/sum_if/avg_if` DSL 契约，而是先补齐一个更保守、可组合、接近现有表达式能力的兼容特性：

```text
sum(if(state == 'sale', amountTotal, 0)) as confirmedAmount
avg(if(state == 'sale', amountTotal, null)) as avgConfirmedAmount
count(if(stage$caption == 'Won', 1, null)) as wonCount
```

## 问题定义

当前 `IF(...)` 已在表达式白名单中，但 JDBC 端并未统一将其降级为标准 SQL `CASE WHEN ... THEN ... ELSE ... END`。这导致：

- MySQL 上部分 `IF(...)` 写法可能可用
- PostgreSQL / SQL Server / SQLite 上不应假设 `IF(...)` 可直接执行
- `sum(if(...))` 这类条件聚合表达在多方言下缺少稳定契约

如果仅允许 LLM 自由生成 `sum(if(...))`，而不补 JDBC 端统一 lowering，则多方言行为会继续不一致。

## 目标

- JDBC 端统一支持 `IF(condition, trueExpr, falseExpr)` 的 SQL 降级
- 聚合函数包裹 `IF(...)` 时，可跨方言稳定生成 `SUM/AVG/COUNT(CASE WHEN ...)`
- 不修改 parser 主语法
- 不修改 semantic service 主流程
- 不修改 SQL builder 主逻辑
- 保持现有表达式组合方式：`== / != / > / < / >= / <= / && / || / ! / ()`

## 目标写法

本阶段明确支持以下表达式模式：

```text
sum(if(state == 'sale', amountTotal, 0)) as confirmedAmount
avg(if(state == 'sale', amountTotal, null)) as avgConfirmedAmount
count(if(stage$caption == 'Won', 1, null)) as wonCount
sum(if(stage$caption == 'Won' && state == 'sale', amountTotal, 0)) as wonAmount
```

目标 SQL 形态：

```sql
SUM(CASE WHEN state = 'sale' THEN amount_total ELSE 0 END)
AVG(CASE WHEN state = 'sale' THEN amount_total ELSE NULL END)
COUNT(CASE WHEN stage_caption = 'Won' THEN 1 ELSE NULL END)
SUM(CASE WHEN stage_caption = 'Won' AND state = 'sale' THEN amount_total ELSE 0 END)
```

## 范围

### In Scope

- JDBC 表达式引擎中的 `IF(...)` lowering
- `SUM / AVG / COUNT / MIN / MAX` 等聚合函数包裹 `IF(...)` 的跨方言可执行性
- `SqlFragment` 聚合 / 类型信息在该场景下的正确传播
- 相关单元测试与集成测试
- 相关文档更新

### Out of Scope

- 本阶段不新增正式 DSL 函数：`count_if / sum_if / avg_if`
- 本阶段不开放用户直接写 `CASE WHEN` 原生 SQL 片段
- 本阶段不修改 parser 主语法
- 本阶段不修改 semantic service 主流程
- 本阶段不修改 SQL builder 主逻辑
- 本阶段不扩展 Python Odoo 内嵌引擎

## 方案约束

- 对外仍以“现有 calculated field / inline expression 体系”承载能力
- JDBC 端统一使用 `CASE WHEN` 作为 lowering 目标，避免依赖某个数据库的 `IF(...)`
- Mongo 端已有 `$cond` 能力，本阶段以只读确认兼容为主，不要求改动
- planner / prompt 后续可将该能力作为过渡兼容能力，不把它当最终 DSL 契约

## 设计决策

### 1. 不在聚合函数层发明隐式条件语义

不做类似“`sum(xxx)` 且第一个参数像布尔表达式时自动猜条件”的隐式规则。

原因：

- 语义不透明
- 参数类型难稳定判断
- 容易放大 LLM 生成不稳定表达式

### 2. 以 `IF(...)` 为显式条件容器

本阶段只支持“显式条件容器 + 常规聚合函数”模式。

好处：

- 组合性清晰
- 与现有表达式体系一致
- 后续升级到 `sum_if/count_if` 时可以平滑复用 lowering 逻辑

### 3. JDBC 端统一改写为 `CASE WHEN`

不依赖 MySQL `IF(...)`，以标准 SQL `CASE WHEN` 作为统一中间表达。

## 兼容性要求

- MySQL：行为与现有可用表达兼容，不引入回归
- PostgreSQL：`IF(...)` 必须被改写，不能原样输出
- SQL Server：`IF(...)` 必须被改写，不能原样输出
- SQLite：`IF(...)` 必须被改写，不能原样输出
- Mongo：保持现有 `$cond` 语义，不强制本次改动

## 验收标准

- `sum(if(cond, amount, 0))` 在 JDBC 多方言测试下可生成并执行
- `avg(if(cond, amount, null))` 在 JDBC 多方言测试下可生成并执行
- `count(if(cond, 1, null))` 在 JDBC 多方言测试下可生成并执行
- 聚合表达式仍能被 `InlineExpressionPreprocessStep` 正确识别为 aggregate
- 不影响现有 `SUM/AVG/COUNT` 非 `IF` 场景
- 不影响现有 `fieldAccess / deniedColumns / groupBy / orderBy / alias` 主链路

## 风险与边界

- `IF(...)` 的 else 分支语义必须由调用方显式写出，否则 `SUM / AVG / COUNT` 结果会被误导
- `AVG(IF(..., amount, 0))` 与 `AVG(IF(..., amount, NULL))` 语义不同，本阶段不替调用方自动修正
- `COUNT(IF(...))` 只接受用户显式传出 `1/null` 这类模式，不在本阶段新增语义糖衣
- 该能力解决的是“跨方言兼容”问题，不是“最终 DSL 易用性”问题

## 后续演进建议

本特性落地后，可将 `count_if / sum_if / avg_if` 作为下一阶段正式 DSL 契约：

- `count_if(cond)` → `COUNT(CASE WHEN cond THEN 1 ELSE NULL END)`
- `sum_if(measure, cond)` → `SUM(CASE WHEN cond THEN measure ELSE 0 END)`
- `avg_if(measure, cond)` → `AVG(CASE WHEN cond THEN measure ELSE NULL END)`

当前阶段优先补底层 lowering 能力，为后续 DSL 函数铺路。

## 跟踪维度

- 开发进度：`待开始`
- 测试进度：`待开始`
- 体验进度：`N/A`

---

## 最终签收跳转（2026-04-20）

本文档已于 2026-04-19 标记 `superseded`，所有工作项并入 `REQ-FORMULA-EXTEND-non-aggregation-functions` (v1.4)。

**上游 REQ 已签收**（`accepted-with-risks`，2026-04-20）：

- 最终签收记录：`D:/foggy-projects/foggy-data-mcp/docs/v1.4/acceptance/REQ-FORMULA-EXTEND-non-aggregation-functions-acceptance.md`
- 覆盖审计：`D:/foggy-projects/foggy-data-mcp/docs/v1.4/coverage/REQ-FORMULA-EXTEND-non-aggregation-functions-coverage-audit.md`
- Spec v1 实装对照表：`D:/foggy-projects/foggy-data-mcp/docs/v1.4/formula-spec-v1/parity.md §11`
- Java 质量闸门报告：`D:/foggy-projects/foggy-data-mcp/docs/v1.4/REQ-FORMULA-EXTEND-java-quality-gate-report.md` (`conditional-passed`)

本文档（需求）的核心能力（`IF → CASE WHEN` lowering + `sum/avg/count(if(...))` 聚合组合）已由上游 REQ 完成并证明：

- Java SQLite 1055 passed / MySQL 147 / PG 147 · MSSQL 单元类全绿（F-M3-3 集成 103+44 blocked 为本机 JDBC 环境问题）
- Java `DialectAwareFunctionExp` 14 tests × 4 profile
- 双端 parity 41 positive catalog + 双端安全 20+14 cases
- Odoo Pro embedded vendored 同步 · fast 510+1 xfailed / demo 27 passed

本文件仅作历史快照保留，后续进度 / 签收状态 / follow-up 跟踪一律以上游 REQ 文档为准。
