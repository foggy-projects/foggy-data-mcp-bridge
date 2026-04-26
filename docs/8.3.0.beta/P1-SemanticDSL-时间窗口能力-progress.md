---
type: progress
version: 8.3.0.beta
req_id: P1-SemanticDSL-TimeWindow
priority: P1
status: in-progress
last_updated: 2026-04-26
---

# Compose Query · `timeWindow` SemanticDSL — Progress

> 状态口径：`not-started` / `in-design` / `in-progress` / `blocked` / `ready-for-review` / `accepted` / `rejected`

## 关联规范文档

- 设计稿：`P1-SemanticDSL-时间窗口能力设计.md`（design `draft`）
- 上游主语义：`../8.2.0.beta/P1-ComposeQuery-时间分析能力增强-需求.md`
- 上游 progress：`../8.2.0.beta/P1-ComposeQuery-时间分析能力增强-progress.md`
- Metadata 配套（`timeRole`）：`P2-Metadata时间维度与属性分析报告.md`
- Metadata 配套（样例行）：`P2-Metadata时间维度样例行-需求.md`

## 当前阶段判断

- 当前阶段：`in-progress`（DSL 包装层 Java 实现已落盘 + 11 parity fixture；YoY / rolling_7d / MTD / YTD 已补真实 SQL parity；SQLite / PostgreSQL / SQL Server lane 通过，MySQL 5.7 非窗口编排通过且窗口用例按能力检测跳过；待 MySQL 8 lane + 文档收尾）
- 当前目标：让 `SemanticQueryRequest.timeWindow` 这条 LLM-facing JSON 路径从「design draft + Java 代码先行」推进到 `ready-for-review`
- 当前范围：仅 Java 主仓 `compose/plan/TimeWindow*` + parity catalog；Python 镜像独立跟踪

## 前置条件检查表

| item | 说明 | 状态 |
|---|---|---|
| 上游 P1 主语义已落盘 | `WindowColumn / OverClause / WindowFrame` + `JoinPlan + DerivedQueryPlan` | `done`（M2 + M6 已 ready-for-review） |
| `timeRole=business_date` 元数据 | DSL `field` 字段需要靠它选择正确的时间轴 | `done`（Java/Python 已实现） |
| Columns API 收口 | `TimeWindowExpander` 通过 `.columns(List<?>)` 写出 plan | `done`（8.3 P2 已 accepted 2026-04-26） |
| TM 演示模板 timeRole 配置 | `salesDate` 维度需显式 `timeRole: 'business_date'` | `pending`（见设计稿 §前提与依赖） |
| 时间维度样例行（metadata） | LLM 需要样例值理解 `salesDate$id / caption / 年月日` 形态 | `draft`（见 P2-Metadata时间维度样例行-需求.md） |

## Step 追踪

| step | 内容 | 状态 | 备注 |
|---|---|---|---|
| S0 | 设计稿创建 | `completed` | `P1-SemanticDSL-时间窗口能力设计.md`（design `draft`） |
| S1 | DSL value object · `TimeWindowDef` | `completed` | record 91 行，含 `fromMap(Map)` JSON 反序列化 + `isComparative/isCumulative/isRolling/rollingWindowSize` 分类辅助；构造期 fail-closed 校验 `field/grain/comparison` 必填 |
| S2 | 语义校验器 · `TimeWindowValidator` | `completed` | 163 行，覆盖 grain × comparison 兼容矩阵（设计稿 §兼容矩阵）；错误码 `GRAIN_INCOMPATIBLE / RANGE_INVALID` 等；14 tests 全绿 |
| S3 | 相对日期表达式解析 · `RelativeDateParser` | `completed` | 137 行，`now / -1Y / -7D / -1M / -1Q` 文法 → 四方言 dialect-aware SQL（MySQL `DATE_SUB` / PG `INTERVAL` / MSSQL `DATEADD` / SQLite `DATE('now', ...)`）；24 tests 全绿 |
| S4 | DSL → AST 展开 · `TimeWindowExpander` | `completed` | 312 行，把 `timeWindow` JSON 展开为 `JoinPlan + DerivedQueryPlan` AST（同环比 = self-join 当期/前期，累计 = 窗口聚合，rolling = ROWS BETWEEN N PRECEDING AND CURRENT ROW）；11 expansion tests 全绿 |
| S5 | Parity catalog（11 fixture） | `completed` | `src/test/resources/parity/timeWindow/` · 7 happy + 4 negative |
| S6 | 集成测试 · 真实 SQL 数据比对 | `completed` | `ComparativeExecutionIntegrationTest` 1 test · sqlite lane 真实 SQL 比对通过（同 demo TM/QM 链路） |
| S7 | PostgreSQL identifier quoting + dialect propagation 修复 | `completed` | commit `9f44ba8 fix(timeWindow)...` |
| S8 | `SemanticQueryRequest.timeWindow` 字段 + Controller / Tool 接入 | `completed` | `SemanticQueryRequest.timeWindow` 已存在；本轮补齐 `DslQueryFunction` / `ComposedDataSetResult` 参数映射、`SemanticQueryServiceV3Impl.generateSql` preview 编排路径与 `QueryFacadeImpl.buildSqlOnly` 拦截协同 |
| S9 | LLM-facing schema · `query_model_v3_schema.json` 暴露 timeWindow | `completed` | `foggy-dataset-mcp/src/main/resources/schemas/query_model_v3_schema.json` 已新增 `payload.timeWindow` shape，`descriptions/query_model_v3.md` 已补使用说明；`ToolConfigLoaderTest` 8 tests 全绿 |
| S10 | 跨方言 lane 全量验收（sqlite ✅ / MySQL / PG / MSSQL） | `partial` | SQLite ✅ / PostgreSQL ✅ / SQL Server ✅；MySQL 5.7 ✅（非窗口 compose + comparative 通过，timeWindow 4 tests 因无窗口函数 skipped）；MySQL 8 待补 |
| S11 | 设计稿 status `draft` → `accepted` | `pending` | 待 S10 跨方言 lane 完成 |
| S12 | Python parity 镜像 | `deferred` | Java 端 11 个 parity fixture 已可作为契约；Python 端独立立项跟踪 |
| S13 | CTE 编排真实 SQL parity 补强 | `completed` | `ComposeRealSqlParityTest` 覆盖 derived/filter、join aggregate、union all aggregate；真实执行结果与手写 SQL 逐行比较 |
| S14 | YoY prior 关联缺陷回归 | `completed` | YoY prior 分支补 shifted period key join，防止同月跨年比较错误匹配当前期 |
| S15 | rolling / cumulative 执行链路真实 SQL parity | `completed` | `TimeWindowExecutionIntegrationTest` 覆盖 rolling_7d / MTD / YTD；均通过真实 semantic query 执行并与手写 SQL 逐行比较 |
| S16 | rolling / cumulative 从 calculatedFields 切换为 Compose plan 执行 | `completed` | 避免 `SUM(metric) OVER (...)` 作为 inline calculated field 被聚合校验误判；同时修正窗口分区，剔除当前时间桶，仅按非时间维度 + 年/月重置分区 |
| S17 | SQL preview / Compose DSL / MCP schema 接入补齐 | `completed` | 新增 `rolling_7d SQL preview matches hand-written SQL`，验证 `generateSql` 输出可直接执行并与手写 SQL 逐行比较；`ScriptRuntimeTest` 覆盖 `dsl()` timeWindow 请求映射 |
| S18 | SQL Server nested CTE fallback 修复 | `completed` | `mssql/sqlserver` compose 编排改为子查询 fallback，避免 SQL Server 不支持 `FROM (WITH ...)` 嵌套 CTE；`DialectFallbackTest` 覆盖该方言策略 |

## 验收对照（来自设计稿 §验收 + 上游 P1 §验收）

| AC | 验收点 | 状态 | 证据 |
|---|---|---|---|
| AC-1 | `TimeWindowDef` 接受 `field/grain/comparison/range/value/targetMetrics/rollingAggregator` 7 字段，构造期 fail-closed | ✅ `passed` | `TimeWindowDef.java` record + 单元测试覆盖 |
| AC-2 | grain × comparison 兼容矩阵全部覆盖 | ✅ `passed` | `TimeWindowValidatorTest$ErrorCodes` 11 tests + `HappyPaths` 3 tests |
| AC-3 | 8 种 comparison（yoy/mom/wow/ytd/mtd/rolling_7d/30d/90d）展开为合法 SQL | ✅ `passed` | `TimeWindowExpanderTest$ComparativeExpansion` 6 + `CumulativeExpansion` 2 + `RollingExpansion` 3 |
| AC-4 | 相对日期表达式四方言 lowering | ✅ `passed` | `RelativeDateParserTest` 24 tests 覆盖 MySQL / PG / MSSQL / SQLite SQL lowering |
| AC-5 | 真实 SQL 数据比对（项目集成测试规范） | ✅ `passed`（SQLite / PostgreSQL / SQL Server） / ✅ MySQL 5.7 capability-skip | `ComparativeExecutionIntegrationTest` 1 test + `TimeWindowExecutionIntegrationTest` 4 tests；MySQL 5.7 不支持窗口函数，4 个 timeWindow execution tests skipped |
| AC-6 | Parity catalog 与上游 P1 测试基线对齐 | ✅ `passed` | 11 fixture（7 happy + 4 negative）已落盘 |
| AC-7 | `SemanticQueryRequest` POJO + Controller / Tool 接入 | ✅ `passed` | POJO 字段已存在；Compose DSL / composed result / generateSql preview 路径已补映射，`ScriptRuntimeTest` 覆盖请求透传 |
| AC-8 | `query_model_v3_schema.json` 暴露 timeWindow shape | ✅ `passed` | MCP schema 与 query_model_v3 使用说明已补；`ConvertFrom-Json` 解析通过，`ToolConfigLoaderTest` 8 tests 全绿 |
| AC-9 | 跨方言 lane 全量验收 | ⏳ `partial` | SQLite ✅ / PostgreSQL ✅ / SQL Server ✅ / MySQL 5.7 ✅（窗口用例 skipped）；MySQL 8 待补 |
| AC-10 | 设计稿 status 转 `accepted` | ⏳ `pending` | 待 AC-9 跨方言 lane 通过后 |

## 当前测试基线

```bash
mvn -pl foggy-dataset-model "-Dtest=TimeWindowDefTest,TimeWindowExpanderTest,\
TimeWindowValidatorTest,RelativeDateParserTest,ComparativeExecutionIntegrationTest" \
-Dspring.profiles.active=sqlite -P!multi-db test
```

→ **50 passed / 0 failures / 0 skipped**（2026-04-26）

补强回归（sqlite lane · 2026-04-26）：

```bash
mvn "-Dtest=ComposeSqlCompilerTest,DerivedLoweringTest,JoinCompileTest,UnionCompileTest,\
FluentApiCompileTest,SchemaDerivationTest,AuthorityResolutionPipelineTest,\
BaseModelPlanCollectorTest,ScriptRuntimeTest,SandboxLayerCTest,TimeWindowValidatorTest,\
TimeWindowExpanderTest,RelativeDateParserTest,FluentApiTest,ComposeRealSqlParityTest,\
ComparativeExecutionIntegrationTest,TimeWindowExecutionIntegrationTest" "-P!multi-db" test
```

→ **247 passed / 0 failures / 1 skipped**

本轮接入回归（sqlite lane · 2026-04-26）：

```bash
mvn "-Dtest=TimeWindowExecutionIntegrationTest,ScriptRuntimeTest,ComparativeExecutionIntegrationTest,\
ComposeRealSqlParityTest,TimeWindowExpanderTest,TimeWindowValidatorTest,RelativeDateParserTest" \
"-P!multi-db" test
```

→ **75 passed / 0 failures / 0 skipped**

```bash
cd foggy-dataset-mcp
mvn "-Dtest=ToolConfigLoaderTest" test
```

→ **8 passed / 0 failures / 0 skipped**

跨方言真实 SQL parity 回归（2026-04-26）：

```bash
mvn "-Dtest=ComposeRealSqlParityTest,ComparativeExecutionIntegrationTest,TimeWindowExecutionIntegrationTest" \
"-Dspring.profiles.active=postgres" "-P!multi-db" test
```

→ **8 passed / 0 failures / 0 skipped**

```bash
mvn "-Dtest=ComposeRealSqlParityTest,ComparativeExecutionIntegrationTest,TimeWindowExecutionIntegrationTest" \
"-Dspring.profiles.active=docker" "-P!multi-db" test
```

→ **4 passed / 0 failures / 4 skipped**（MySQL 5.7 无窗口函数，timeWindow execution 4 tests skipped）

```bash
mvn "-Dtest=ComposeRealSqlParityTest,ComparativeExecutionIntegrationTest,TimeWindowExecutionIntegrationTest" \
"-Dspring.profiles.active=sqlserver" "-P!multi-db" test
```

→ **8 passed / 0 failures / 0 skipped**

```bash
mvn "-Dtest=DialectFallbackTest" "-P!multi-db" test
```

→ **16 passed / 0 failures / 0 skipped**

| 测试类 | 数量 |
|---|---:|
| `TimeWindowExpanderTest$ComparativeExpansion` | 6 |
| `TimeWindowExpanderTest$CumulativeExpansion` | 2 |
| `TimeWindowExpanderTest$RollingExpansion` | 3 |
| `TimeWindowValidatorTest$ErrorCodes` | 11 |
| `TimeWindowValidatorTest$HappyPaths` | 3 |
| `RelativeDateParserTest` | ~24 |
| `ComparativeExecutionIntegrationTest` | 1 |
| `TimeWindowExecutionIntegrationTest` | 4 |
| `ScriptRuntimeTest` | 18 |

## 已落盘文件清单

### 主代码（compose/plan · 4 文件 · ~703 行）

| 文件 | 行数 | 用途 |
|---|---:|---|
| `compose/plan/TimeWindowDef.java` | 91 | DSL value object（record） |
| `compose/plan/TimeWindowValidator.java` | 163 | grain × comparison 兼容矩阵 |
| `compose/plan/RelativeDateParser.java` | 137 | `now / -1Y / -7D` 解析 + 四方言 lowering |
| `compose/plan/TimeWindowExpander.java` | 312 | DSL → `JoinPlan + DerivedQueryPlan` AST |

### 测试代码（5 文件 · ~1,251 行）

| 文件 | 行数 |
|---|---:|
| `test/.../compose/plan/TimeWindowExpanderTest.java` | 308 |
| `test/.../compose/plan/TimeWindowValidatorTest.java` | 184 |
| `test/.../compose/plan/RelativeDateParserTest.java` | 160 |
| `test/.../semantic/ComparativeExecutionIntegrationTest.java` | 83 |
| `test/.../semantic/TimeWindowExecutionIntegrationTest.java` | 242 |
| `test/.../engine/compose/runtime/ScriptRuntimeTest.java` | 556 |

### Parity catalog（11 fixture）

`src/test/resources/parity/timeWindow/`：

**Happy paths（7）**

| fixture | comparison | grain |
|---|---|---|
| `mom-month-happy.json` | mom | month |
| `wow-week-happy.json` | wow | week |
| `mtd-day-happy.json` | mtd | day |
| `ytd-month-happy.json` | ytd | month |
| `yoy-month-happy.json` | yoy | month |
| `rolling_7d-day-happy.json` | rolling_7d | day |
| `rolling_30d-day-happy.json` | rolling_30d | day |

**Negative paths（4）**

| fixture | 错误码 |
|---|---|
| `yoy-month-negative-range-invalid.json` | `RANGE_INVALID` |
| `yoy-day-negative-grain-incompat.json` | `GRAIN_INCOMPATIBLE` |
| `mom-week-negative-grain-incompat.json` | `GRAIN_INCOMPATIBLE` |
| `rolling_7d-month-negative-grain-incompat.json` | `GRAIN_INCOMPATIBLE` |

### 已 commit 的 fix

```
9f44ba8 fix(timeWindow): fix PostgreSQL identifier quoting and dialect propagation for Comparative Query Planner
```

## 阻塞项

- 当前无硬阻塞
- 待解项（不阻断 in-progress，但阻断转 ready-for-review）：
  - S10 MySQL 8 lane 尚未执行；当前仅覆盖 MySQL 5.7 的非窗口 fallback 与 capability-skip
- Deferred：
  - S12 Python parity 镜像（`foggy-data-mcp-bridge-python` 单独立项）

## 后续衔接

- 下一步建议：
  1. 起 docker MySQL / PG / MSSQL lane 各跑一遍 11 parity fixture + ComparativeExecutionIntegrationTest（S10）
  2. 补 MySQL 8 lane 后转 `ready-for-review`，等签收
  3. 设计稿同步 status `draft` → `accepted`（S11）

## 后置评审要求

- 当前阶段不需要 `foggy-implementation-quality-gate`（待 S10 完成、转 `ready-for-review` 之前再走一次）
- 当前阶段不需要 `foggy-test-coverage-audit`
- 当前阶段不需要 `foggy-acceptance-signoff`

## 自检结论

- 当前交付类型：`record + implementation`
- 当前结论：`code-landed-pending-cross-dialect`
- 已完成：
  - 文档路径落在正确版本目录（`docs/8.3.0.beta/`）
  - 命名与现有约定一致（`P1-SemanticDSL-时间窗口能力-progress.md` 配套设计稿同名前缀）
  - 已与上游 8.2 P1 progress（in-progress · S12 待本文件）建立交叉引用
  - 已交叉引用 8.3 P2 metadata 配套（timeRole + 样例行）
- 已修复：本次 progress 新建关闭了「design 稿 draft / 实际代码已落盘 + 50 tests passed / 无 progress 跟踪」的状态脱节问题
