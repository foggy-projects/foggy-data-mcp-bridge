---
type: progress
version: 8.2.0.beta
req_id: P1-ComposeQuery-TimeAnalytics
status: in-progress
priority: P1
last_updated: 2026-04-26
---

# 8.2.0.beta Compose Query 时间分析能力增强 — Progress

> 状态口径：`not-started` / `in-design` / `in-progress` / `blocked` / `ready-for-review` / `accepted` / `rejected`

## 关联规范文档

- 需求：`P1-ComposeQuery-时间分析能力增强-需求.md`
- 评估：`P1-ComposeQuery-时间分析能力增强评估.md`
- 上游基线能力：`P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`
- 能力对比参考：`P0-ComposeQuery-固定Schema下业务分析能力对比评估.md`
- DSL 包装层（下游接续）：`../8.3.0.beta/P1-SemanticDSL-时间窗口能力设计.md`
- DSL 包装层 progress：`../8.3.0.beta/P1-SemanticDSL-时间窗口能力-progress.md`

## 当前阶段判断

- 当前阶段：`in-progress`（首批四层中第一层「窗口分析」+ 第二层「时间偏移与同环比」+ 第三层「区间累计 / rolling」核心实现已落盘；SQLite / PostgreSQL / SQL Server 真实 SQL parity 通过，MySQL 5.7 非窗口编排通过且窗口用例按能力检测跳过；尚待 MySQL 8 lane + 文档收尾 + Python parity 镜像）
- 当前目标：把第一批四层中已实现的能力（窗口/同环比/累计/rolling）从「working tree 已存在」推进到 `ready-for-review`
- 当前范围：Java 主仓 `compose/plan` + `semantic` 包 + parity catalog；Python 镜像作为独立 follow-up 跟踪

## 前置条件检查表

| item | 说明 | 状态 |
|---|---|---|
| `QueryPlan` 主语义稳定 | 时间分析基于既有派生查询 / 阶段切断 / `JOIN` / `UNION` 能力展开 | `done`（M2 + M6 已 ready-for-review） |
| 时间分析评估稿完成 | 作为正式需求的上游输入 | `done` |
| 时间粒度建模基线明确 | TM `timeRole=business_date` 已在 Java/Python 实现 | `done`（见 8.3 P2-Metadata时间维度与属性分析报告.md） |
| Columns API 收口 | `TimeWindowExpander` 通过 `.columns(List<?>)` 写出 plan，依赖 P2 收口 | `done`（8.3 P2 已 accepted 2026-04-26） |
| 是否引入完整财务日历 | 当前未纳入第一批范围 | `out-of-scope` |

## Step 追踪

| step | 内容 | 状态 | 备注 |
|---|---|---|---|
| S1 | 补写时间分析能力评估稿 | `completed` | `P1-ComposeQuery-时间分析能力增强评估.md` 已创建 |
| S2 | 上升为正式需求文档 | `completed` | `P1-ComposeQuery-时间分析能力增强-需求.md` 已创建 |
| S3 | 明确优先级与非目标 | `completed` | 已在需求稿中冻结为窗口 / 偏移比较 / 区间累计 / 补桶四层 |
| S4 | 实现规划 → DSL 包装层契约 | `completed` | 直接落到 8.3.0.beta P1-SemanticDSL `timeWindow` JSON 包装层契约（design `draft`），未单独再起 Java 实现规划文档 —— 实现以代码先行 |
| S5 | 第一层「窗口分析」AST 节点 | `completed` | `WindowColumn` / `OverClause` / `WindowFrame` / `WindowColumnBuilder` 已落地（M2 阶段产出，跨 8.2 P0 / 本 P1 共享） |
| S6 | 第二层「同环比 yoy/mom/wow」+ 第三层「累计 ytd/mtd」+ rolling 7d/30d/90d | `completed` | `TimeWindowDef` (91L) + `TimeWindowExpander` (312L) + `TimeWindowValidator` (163L) + `RelativeDateParser` (137L) 已落盘，4 文件总计 **703 行主代码** |
| S7 | parity catalog 落 11 个 fixture | `completed` | `src/test/resources/parity/timeWindow/` 已落 `mom-month-happy / wow-week-happy / mtd-day-happy / ytd-month-happy / yoy-month-happy / yoy-month-negative-range-invalid / yoy-day-negative-grain-incompat / mom-week-negative-grain-incompat / rolling_7d-day-happy / rolling_30d-day-happy / rolling_7d-month-negative-grain-incompat`，11 个 happy + negative case |
| S8 | 单元 / 集成测试 | `completed` | `TimeWindowDefTest` + `TimeWindowExpanderTest` (308L) + `TimeWindowValidatorTest` (184L) + `RelativeDateParserTest` (160L) + `ComparativeExecutionIntegrationTest` (83L) · sqlite lane **50 passed / 0 failures** |
| S9 | PostgreSQL identifier quoting + dialect propagation 修复 | `completed` | commit `9f44ba8 fix(timeWindow): fix PostgreSQL identifier quoting and dialect propagation for Comparative Query Planner` |
| S10 | 第四层「连续时间轴补桶」 | `not-started` | 设计稿与需求稿都明确把这一层挂在第一批之外，留给后续 |
| S11 | 跨方言 lane 全量验收（MySQL / PG / MSSQL / SQLite） | `partial` | SQLite ✅ / PostgreSQL ✅ / SQL Server ✅；MySQL 5.7 ✅（非窗口 compose + comparative 通过，timeWindow 4 tests 因无窗口函数执行 logged no-op）；MySQL 8 待补 |
| S12 | DSL 包装层签收（8.3.0.beta P1-SemanticDSL） | `partial` | `SemanticQueryRequest.timeWindow` / Compose DSL / MCP `query_model_v3_schema.json` 已接入；待跨方言 lane 后签收 |
| S13 | Python parity 立项与镜像 | `deferred` | Java 端 11 个 parity fixture 已可作为契约；Python 端单独立项跟踪（暂不在本批节奏内） |
| S14 | 8.2 P0 M10 签收前补 cross-link | `pending` | 8.2 P0 progress M10 行需补「P1 时间分析（窗口/同环比/累计/rolling）已 in-progress，第四层连续轴 deferred」 |
| S15 | CTE / timeWindow 真实 SQL parity 补强 | `completed` | 新增 Compose 编排真实 SQL 对比测试；YoY / rolling_7d / MTD / YTD execution 均改为手写 SQL parity；补 `generateSql` preview SQL 直连执行对照；修复 prior 自关联缺少 shifted period key、rolling/cumulative 分区退化、SQL Server nested CTE fallback 与跨方言派生列引用 quoting 等缺陷 |

## 开发进度

### 已落盘的 Java 实现（working tree · 部分 commit）

**主代码（4 个新文件 · ~703 行）**

| 文件 | 行数 | 责任 |
|---|---:|---|
| `foggy-dataset-model/src/main/java/.../compose/plan/TimeWindowDef.java` | 91 | record + `fromMap` JSON 反序列化 + `isComparative/isCumulative/isRolling/rollingWindowSize` 分类辅助 |
| `foggy-dataset-model/src/main/java/.../compose/plan/TimeWindowExpander.java` | 312 | 把 `timeWindow` 展开为 `JoinPlan + DerivedQueryPlan` AST，完成 SQL 自动生成 |
| `foggy-dataset-model/src/main/java/.../compose/plan/TimeWindowValidator.java` | 163 | grain × comparison 兼容矩阵校验，错误码 `GRAIN_INCOMPATIBLE / RANGE_INVALID` 等 |
| `foggy-dataset-model/src/main/java/.../compose/plan/RelativeDateParser.java` | 137 | `now / -1Y / -7D` 等相对表达式解析为绝对日期 SQL（四方言 dialect-aware） |

**测试代码（4 个新文件 · ~735 行）**

| 文件 | 行数 |
|---|---:|
| `foggy-dataset-model/src/test/java/.../compose/plan/TimeWindowExpanderTest.java` | 308 |
| `foggy-dataset-model/src/test/java/.../compose/plan/TimeWindowValidatorTest.java` | 184 |
| `foggy-dataset-model/src/test/java/.../compose/plan/RelativeDateParserTest.java` | 160 |
| `foggy-dataset-model/src/test/java/.../semantic/ComparativeExecutionIntegrationTest.java` | 83 |

**Parity catalog（11 个 fixture）**

`foggy-dataset-model/src/test/resources/parity/timeWindow/`：
- happy：`mom-month-happy / wow-week-happy / mtd-day-happy / ytd-month-happy / yoy-month-happy / rolling_7d-day-happy / rolling_30d-day-happy`（7 条）
- negative：`yoy-month-negative-range-invalid / yoy-day-negative-grain-incompat / mom-week-negative-grain-incompat / rolling_7d-month-negative-grain-incompat`（4 条）

**已 commit 的 fix**

```
9f44ba8 fix(timeWindow): fix PostgreSQL identifier quoting and dialect propagation for Comparative Query Planner
```

### 第四层「连续时间轴补桶」状态

`not-started`。需求稿与设计稿都把它列为第四优先级，第一批不交付。

## 测试进度

- 自动化测试基线（sqlite lane · 2026-04-26）：

  ```bash
  mvn -pl foggy-dataset-model "-Dtest=TimeWindowDefTest,TimeWindowExpanderTest,\
  TimeWindowValidatorTest,RelativeDateParserTest,ComparativeExecutionIntegrationTest" \
  -Dspring.profiles.active=sqlite -P!multi-db test
  ```
  → **50 passed / 0 failures / 0 skipped**

  分布：
  - `TimeWindowExpanderTest`：11（ComparativeExpansion 6 + CumulativeExpansion 2 + RollingExpansion 3）
  - `TimeWindowValidatorTest`：14（ErrorCodes 11 + HappyPaths 3）
  - `RelativeDateParserTest`：~24
  - `ComparativeExecutionIntegrationTest`：1（真实 SQL 数据比对）

- 跨方言验收：SQLite ✅ / PostgreSQL ✅ / SQL Server ✅ / MySQL 5.7 ✅（非窗口 compose + comparative 通过，timeWindow execution 按能力检测 logged no-op，不产生 skipped 计数）/ MySQL 8 pending
- CTE / timeWindow parity 补强（sqlite lane · 2026-04-26）：
  - `ComposeRealSqlParityTest`：派生聚合 + 外层过滤、跨模型 join 聚合、union all 聚合，均与手写 SQL 逐行比较
  - `ComparativeExecutionIntegrationTest`：YoY current / prior / diff / ratio 与手写 SQL 逐行比较
  - `TimeWindowExecutionIntegrationTest`：rolling_7d / MTD / YTD 执行结果与手写 SQL 逐行比较；新增 `generateSql` preview SQL 执行结果与手写 SQL 逐行比较
  - `ScriptRuntimeTest`：`DslQueryFunction` timeWindow 请求映射覆盖
  - `ToolConfigLoaderTest`：MCP `query_model_v3_schema.json` / description 加载通过
  - 相关回归套件：**247 passed / 0 failures / 1 skipped**
  - 本轮接入回归：**75 passed / 0 failures / 0 skipped**（model 相关套件）+ **8 passed / 0 failures / 0 skipped**（MCP ToolConfigLoader）

- 跨方言真实 SQL parity 回归（2026-04-26）：

  ```bash
  mvn "-Dtest=ComposeRealSqlParityTest,ComparativeExecutionIntegrationTest,TimeWindowExecutionIntegrationTest" \
  "-Dspring.profiles.active=postgres" "-P!multi-db" test
  ```

  → **8 passed / 0 failures / 0 skipped**

  ```bash
  mvn "-Dtest=ComposeRealSqlParityTest,ComparativeExecutionIntegrationTest,TimeWindowExecutionIntegrationTest" \
  "-Dspring.profiles.active=docker" "-P!multi-db" test
  ```

  → **8 passed / 0 failures / 0 skipped**（MySQL 5.7 无窗口函数，timeWindow execution 4 tests 记录 info log 后 no-op 返回）

  ```bash
  mvn "-Dtest=ComposeRealSqlParityTest,ComparativeExecutionIntegrationTest,TimeWindowExecutionIntegrationTest" \
  "-Dspring.profiles.active=sqlserver" "-P!multi-db" test
  ```

  → **8 passed / 0 failures / 0 skipped**

  ```bash
  mvn "-Dtest=DialectFallbackTest" "-P!multi-db" test
  ```

  → **16 passed / 0 failures / 0 skipped**

## 体验进度

- experience: `N/A`
- 后续 LLM 实际能填出 `timeWindow` JSON 后，应在 8.3.0.beta P1-SemanticDSL progress 收实测样例

## 需求验收标准对照

| 验收项 | 当前状态 | 说明 |
|---|---|---|
| 需求目标清晰 | `done` | 已说明为什么要补时间分析以及补哪些能力 |
| 非目标明确 | `done` | 已排除完整 SQL、复杂 cohort、完整财务日历等范围 |
| 优先级有序 | `done` | 第一批已落 1-3 层，第四层 deferred |
| 与既有 QueryPlan 语义兼容 | `done` | `TimeWindowExpander` 直接产出 `JoinPlan + DerivedQueryPlan`，沿用阶段切断与字段可见性 |
| 可继续拆规划 | `done` | DSL 包装层已落到 8.3 P1-SemanticDSL；Java 实现先行落地 |
| 跨方言可验收 | `partial` | SQLite ✅ / PostgreSQL ✅ / SQL Server ✅ / MySQL 5.7 ✅（窗口用例 logged no-op）；MySQL 8 待补 |
| 第一批可签收 | `partial` | 待 S11 / S12 / S14 完成 |

## 计划外变更

- **变更 1**（2026-04-26 同步）：第一批实现以「代码先行 / 文档跟进」方式推进，`P1-ComposeQuery-时间分析能力增强-实现规划.md` / `代码清单.md` 未单独起草；DSL 包装层契约直接落到 `../8.3.0.beta/P1-SemanticDSL-时间窗口能力设计.md`。本次 progress 回写补全这一脱节。

## 阻塞项

- 当前无硬阻塞
- 待解项（不阻断 in-progress，但阻断签收）：
  - S11 MySQL 8 lane（当前 MySQL 5.7 仅覆盖非窗口 fallback；窗口函数用例以 info log no-op 收口）
  - S14 8.2 P0 M10 cross-link
- Deferred（不在本批）：
  - S10 第四层连续时间轴补桶
  - S13 Python parity 镜像

## 后续衔接

- 下一步建议：
  1. 补 MySQL 8 lane，确认 timeWindow execution 在 MySQL 8 窗口函数环境下与手写 SQL 逐行一致
  2. 8.3 P1-SemanticDSL 设计稿 status 与 acceptance 收口
  3. 收齐后转 `ready-for-review`
- Python parity 镜像可独立立项（建议放 `foggy-data-mcp-bridge-python/docs/v1.6/` 或其后续版本）

## 后置评审要求

- 当前阶段不需要 `foggy-implementation-quality-gate`（待 S11 完成、转 `ready-for-review` 之前再走一次）
- 当前阶段不需要 `foggy-test-coverage-audit`（同上）
- 当前阶段不需要 `foggy-acceptance-signoff`（同上）

## 自检结论

- 当前交付类型：`record + implementation`
- 当前结论：`code-landed-pending-cross-dialect-and-cross-repo`
- 已完成：
  - 文档路径落在正确版本目录
  - 命名与现有 `docs/8.2.0.beta/` 约定一致
  - development / testing 维度已记录到代码级
  - 正式需求 / 评估稿 / DSL 设计稿 / DSL progress 已交叉链接
- 已修复：本次 progress 回写关闭了「文档显示 in-design + 当前无代码实现 / 实际代码已落盘 ~703 行 + 50 tests passed」的状态脱节问题
