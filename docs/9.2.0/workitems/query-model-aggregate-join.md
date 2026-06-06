---
doc_role: workitem
doc_purpose: Track the Java QueryModel aggregate join implementation for stable semantic projections that must pre-aggregate one-to-many detail before joining.
version: 9.2.0
target: QueryModel Aggregate Join
status: accepted-with-risks
created_at: 2026-05-27
updated_at: 2026-06-06
source_type: requirement
upstream_issue: foggy-projects/foggy-data-mcp-workspace#3
owner_repo: foggy-data-mcp-bridge-wt-dev-compose
owner_module: foggy-dataset-model
---

# QueryModel Aggregate Join

## 文档作用

- doc_type: workitem
- intended_for: execution-agent | reviewer | signoff-owner
- purpose: 记录并推进 Java QueryModel aggregate join 的 9.2.0 实施切片，替代 TMS 稳定投影中依赖 `viewSql` 或调用侧 CTE 编排的右侧预聚合 join。

## Background

TMS 库存、计划、付款、执行段等模型中经常出现左侧主记录天然唯一、右侧明细为 1:N 的结构。普通 QueryModel join 会先产生行倍增，再在最终结果聚合，导致左侧库存数、订单数、金额等指标被重复累加。

历史上可以用 `viewSql` 或 Compose/CTE 在右侧先聚合再 join，但 `viewSql` 是黑盒 SQL。旧数据库或保守优化器可能无法把外层 `WHERE`、租户、站点、时间、状态等条件推入内部聚合，从而先全表聚合再过滤。

本工作项把“右侧先按 join key 聚合，再 join 到左侧”下沉为 QueryModel 结构化能力，让引擎可见右侧模型、groupBy key、slice、measure、join key 和输出字段。

## Target Outcome

- QueryModel 能声明窄版 aggregate join：右侧模型先应用固定 slice / 权限 slice / 可安全下推的请求 slice，再按 join key 聚合，随后以 LEFT JOIN 方式接到主侧。
- 主侧指标不会因右侧 1:N 明细被倍增。
- Java 引擎能按方言选择安全 SQL 形态，优先避免旧数据库依赖 CTE 优化器推导。
- LLM 分析场景可把稳定业务投影视为普通 QM 字段，不需要生成 raw SQL、free CTE 或复杂 Compose recipe。

## Scope

### In Scope

- Java `foggy-dataset-model` QueryModel engine first.
- Same datasource only.
- Initial join type: LEFT JOIN.
- RHS aggregate functions: `SUM`, `COUNT`, `MIN`, `MAX`.
- RHS `groupBy` must cover all right-side join key fields.
- RHS fixed slice must be applied inside aggregate relation.
- Tenant / permission / system slice must remain fail-closed through the normal QueryModel pipeline.
- SQL lowering may use inline derived table, hoisted CTE, or dialect fallback, but the public contract remains semantic aggregate join.
- Tests must include real query execution parity, not only SQL string checks.

### Non-Goals

- No arbitrary `viewSql` expansion.
- No free SQL / free CTE exposed to LLM or caller payloads.
- No cross-datasource federation.
- No many-to-many bridge semantics.
- No materialized preAggregations replacement.
- No public Pivot DSL changes.
- No Python parity in this first Java engine cut; Python follow-up must be tracked separately if needed.

## Module Responsibility

| Area | Owner | Responsibility |
|---|---|---|
| Root workspace | `foggy-data-mcp` | Discussion record and cross-repo context only; no source implementation. |
| Java bridge repo | `foggy-data-mcp-bridge-wt-dev-compose` | Owns 9.2.0 workitem, Java implementation, tests, quality, coverage, and acceptance records. |
| Core engine module | `foggy-dataset-model` | Defines aggregate join contract, loader/builder integration, query planning, SQL lowering, permission preservation, and tests. |
| Demo/test templates | `foggy-dataset-demo` resources consumed by tests | Provide or extend TM/QM fixtures for one-left-row / three-right-detail aggregate join parity. |
| MCP / LLM schema | `foggy-dataset-mcp` | Out of initial implementation unless aggregate join is exposed in public tool schema; later update only after engine contract stabilizes. |

## Code Inventory

| Repo | Path | Role | Expected Change | Notes |
|---|---|---|---|---|
| bridge | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/def/query/` | QueryModel definition objects | update/create | Add a minimal aggregate join definition if existing `joins` object model cannot express it safely. |
| bridge | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/` | QM builder / join graph integration | update | Parse aggregate join declarations, validate grain, expose generated fields. |
| bridge | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query/` | SQL query object and join rendering | update/create | Support joining a structured aggregate relation or derived query object without raw SQL leakage. |
| bridge | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/relation/` | Existing relation wrapping/fallback concepts | read-only-analysis | Reuse concepts if helpful; do not couple QueryModel aggregate join to Compose public API. |
| bridge | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/` | Permission and query lifecycle | read-only-analysis/update if needed | Ensure RHS aggregate relation still receives field/physical/system permission checks. |
| bridge | `foggy-dataset-model/src/test/java/` | Regression and parity tests | update/create | Add real SQL parity and fail-closed tests. |
| bridge | `foggy-dataset-demo/src/main/resources/foggy/templates/` | Test TM/QM templates | update if needed | Add minimal ecommerce-style or dedicated aggregate join fixtures. |
| bridge | `docs/9.2.0/` | Versioned tracking | update | Workitem, progress, quality, coverage, acceptance. |

## Implementation Plan

### Stage 1. Contract and Parser Cut

- Define a narrow aggregate join contract in QM configuration.
- Validate same datasource, LEFT JOIN only, non-empty join keys, non-empty RHS groupBy, and RHS groupBy covering join keys.
- Reject unknown aggregate functions and unknown fields fail-closed.
- Keep contract internal to Java QM first; do not expose through MCP schema until tests and docs are stable.

### Stage 2. Planning and SQL Lowering

- Build RHS aggregate relation as a structured query object, not `viewSql`.
- Apply fixed slice and permission/system slice inside RHS aggregate before groupBy.
- Join RHS aggregate relation to left relation by declared keys.
- Prefer inline derived table or dialect-safe fallback for older database behavior; do not require CTE optimization for correctness or performance.
- Emit debug evidence for `rhsGroupBy`, `joinKeys`, `pushedSlices`, `aggregateFields`, and SQL wrap strategy.

### Stage 3. Field Exposure and Query Behavior

- Expose aggregate output fields as ordinary QueryModel fields or a clearly scoped generated field namespace.
- Support selecting RHS aggregate fields.
- Support `orderBy` on RHS aggregate fields when SQL lowering can prove the alias is available.
- Support `returnTotal` without changing total semantics.

### Stage 4. Tests and Evidence

- Fixture: one left row with stock/count value 100 and three RHS details.
- Assert left-side measure remains 100 after aggregate join.
- Assert RHS aggregate equals the sum/count of the three details.
- Cover RHS fixed status slice.
- Cover tenant/system slice propagation.
- Cover invalid groupBy/join key mismatch refusal.
- Cover `orderBy` aggregate field and `returnTotal`.
- Run targeted Maven tests with SQLite. Add MySQL/PostgreSQL evidence if dialect-specific lowering changes are nontrivial.

## Acceptance Criteria

- Main-side measures are not multiplied by RHS detail row count.
- RHS aggregate fields are correct and null-safe under LEFT JOIN no-match behavior.
- RHS aggregate SQL includes pushed fixed/system slices before groupBy.
- Invalid aggregate join declarations fail closed with user-facing semantic errors.
- No public raw SQL/CTE is introduced.
- Real query execution parity tests pass.
- Progress, quality gate, coverage audit, and acceptance records are created or updated before signoff.

## Architecture Decision: Runtime Aggregate Now, ETL Deferred

Decision: aggregate join is the current Java engine delivery path. ETL / pre-aggregation promotion is a later optimization line and is explicitly out of scope for this cut.

For TMS-style projections, the long-term preferred performance path may still become ETL, pre-aggregated tables, snapshots, or materialized views when the grain and business口径 are stable. Aggregate join is still needed now because it gives the Java engine and LLM-facing model layer a structured way to express "aggregate the 1:N side before joining" while the model is being validated, without falling back to `viewSql`, free CTEs, or caller-authored raw SQL.

Layering rule:

- Development / exploration: use QueryModel aggregate join so the model keeps right-side grain, filters, measures, lineage, and join keys visible to the engine.
- Stabilization: collect query evidence, SQL shape, row-scan behavior, latency, and user-facing query frequency.
- Future production optimization: after this Java semantic path is stable, promote selected high-frequency aggregate joins to ETL / pre-aggregated / materialized models, then expose those models through ordinary TM/QM fields.
- Fallback: if a materialized aggregate is fresh and available, prefer it; otherwise use runtime aggregate join for supported shapes.

Promotion triggers:

- RHS aggregate scans a large table or repeatedly aggregates the same TMS fact/detail table.
- Query is high-frequency, dashboard-backed, or SLA-sensitive.
- Business grain and filters are stable, for example order, waybill, inventory, plan, settlement, tenant, site, date, or status dimensions.
- Target legacy database `EXPLAIN` shows broad aggregation before selective filtering, or p95 latency is not acceptable.
- The same aggregate relation appears across multiple QMs or LLM analysis workflows.

Non-trigger cases:

- Ad hoc analysis where the口径 is still changing.
- Low-frequency model validation queries.
- Queries that require fresh live deltas and do not yet have a reliable refresh/backfill policy.
- Temporary LLM exploration where creating ETL would add governance, lineage, storage, and freshness cost before the need is proven.

LLM-facing rule:

- LLMs should consume stable QM fields and semantic metadata, not synthesize raw SQL/CTE for pre-aggregation.
- Runtime aggregate join keeps exploratory and development queries understandable to the engine.
- Once promoted, the same semantic field can be backed by a materialized aggregate source, with freshness and lineage metadata explaining the data boundary.

## Implementation Notes

Initial Java cut exposes aggregate join through the existing QM V2 table proxy DSL:

```js
fo.leftJoinAggregate(fs)
    .groupBy(fs.orderId)
    .filterEq(fs.orderStatus, 'COMPLETED')
    .sum(fs.salesAmount, 'salesAggAmount')
    .count('salesLineCount')
    .on(fo.orderId, fs.orderId)
```

The parser lowers this into an engine-generated right-side aggregate relation, then joins it as a LEFT derived table:

```sql
left join (
    select agg_src.order_id orderId,
           sum(agg_src.sales_amount) salesAggAmount,
           count(*) salesLineCount
    from fact_sales agg_src
    where agg_src.order_status = 'COMPLETED'
    group by agg_src.order_id
) t2 on t1.order_id = t2.orderId
```

This cut intentionally uses the existing derived-query carrier internally, but the SQL is generated from a structured aggregate join builder. QM authors and LLM/caller payloads are not given free SQL or free CTE surface.

### DSL Direction: Aggregate Relation First

The preferred long-term DSL is relation-level aggregation, not join-level aggregation:

```js
const sales = loadTableModel('FactSalesModel');

const fs = sales
    .filterEq(sales.orderStatus, 'COMPLETED')
    .groupBy(sales.orderId)
    .as('fsByOrder');

fo.leftJoin(fs).on(fo.orderId, fs.orderId)
```

Semantic rule:

- `.filterEq/.filterIn/...` before `.groupBy(...)` belongs to the RHS relation and must be rendered inside the derived aggregate relation before `GROUP BY`.
- `.groupBy(...)` turns a base table model proxy into an aggregate relation proxy with one row per group key.
- Normal `fo.leftJoin(fs).on(...)` remains the join surface; the aggregate nature is carried by the right relation.
- Measures exposed by the aggregate relation default to the source TM measure aggregation metadata, for example `salesAmount` uses the TM/default `SUM` aggregation.
- Non-group dimensions/properties may be used by relation filters before aggregation, but must not be selected from the aggregate relation unless they are group keys.
- `leftJoinAggregate(...)` remains as a compatibility/MVP API, but new model authoring should prefer aggregate relation first once tests stabilize.

Example SQL shape:

```sql
left join (
    select agg_src.order_id orderId,
           sum(agg_src.sales_amount) salesAmount
    from fact_sales agg_src
    where agg_src.order_status = 'COMPLETED'
    group by agg_src.order_id
) fsByOrder on t1.order_id = fsByOrder.orderId
```

Implemented constraints:

- `leftJoinAggregate` compatibility API and aggregate-relation-first DSL are both supported in the initial Java cut.
- Same-source TM relation through the current QueryModel loader path.
- Controlled RHS `groupBy`, `sum`, `avg`, `min`, `max`, `count`, `countDistinct`, and fixed `filter*` methods.
- Aggregate relation default outputs are generated from source TM measures with supported `DbAggregation` metadata.
- RHS `groupBy` must cover the right-side join key.
- `on` must be equality from left model field to right model field.
- Aggregate output aliases must be simple identifiers.
- No-match behavior remains normal LEFT JOIN null behavior.

### Runtime RHS Filter From Request Context

2026-06-04 TMS 上游反馈要求 aggregate join RHS relation filter 支持从运行时上下文读取参数，典型形态如下：

```js
const occupancyByOrder = occ
    .filterEq(occ.suggestionSheetId, (ctx) => ctx.extData.suggestionSheetId)
    .groupBy(occ.tenantId, occ.planningStationId, occ.sourceOrderId)
    .as('occupancyByOrder');
```

当前 Java engine 已补齐该能力并进入上游测试期：

- `QueryRequest.extData` / `SemanticQueryRequest.extData` 会合并到 `ModelResultContext.extData`。
- RHS aggregate relation filter 中的 function 右值在 SQL 生成期按当前 `ModelResultContext` 求值。
- 该 filter 只渲染到 RHS derived aggregate relation 内部，不提升为主查询 `WHERE`，避免 LEFT JOIN 主侧数据被误过滤。
- 缺少运行时值时 fail closed，当前错误表现为 aggregate relation runtime filter 值不能为空。
- 当前 RHS 动态值暂未通过 derived relation 参数通道渲染为 `?`，字符串字面量使用严格白名单限制，只允许字母、数字、下划线、中划线并限制长度；数值、布尔、枚举和标量集合按安全标量处理。
- 当前 aggregate relation 仍要求 RHS `groupBy`，无 `groupBy` 的 RHS relation 是否支持需要单独语义设计或由上游测试确认后再扩展。

Data-viewer 入口要求：

- `/jdbc-model/query-model/v2/{model}` 已可通过 `param.extData` 进入 `ModelResultContext.extData`。
- `/data-viewer/api/query/direct/{qmModel}` 与 `/data-viewer/api/query/{model}/{queryId}/data` 需要将 `ViewerQueryRequest.extData` 透传到 `DbQueryRequestDef.extData`。
- 透传边界仅为 request -> `DbQueryRequestDef.extData` -> `ModelResultContext.extData`，不得自动转换为 `slice` / `where`。
- `query/create` 缓存链路如携带 `payload.extData`，应保留在 cached query context；执行时请求级 `extData` 可覆盖或合并缓存值。

Known limits in this cut:

- RHS fixed filters are rendered as engine-generated SQL literals because the current derived-query carrier has no parameter-binding channel. Values are escaped, but a later cut should add parameter-carrying derived relations.
- Relation-level default aggregation currently renders all supported source TM measures in the RHS derived relation. This keeps the first cut deterministic and model-driven, but a later optimization should prune to only QM-referenced aggregate fields.
- Query-time request slice now has a conservative RHS pushdown path: direct aggregate relation group-key filters are duplicated into RHS `WHERE`, aggregate measure filters are duplicated into RHS `HAVING`, and left join-key filters are mirrored into the RHS source key domain. Structured accessBuilder field-ref guards, for example `context.query.and(fo.orderId, ...)`, use the same safe join-key pushdown path. The outer QueryModel filter is retained to preserve LEFT JOIN result semantics.
- Tenant/access guards can be pushed only when they are expressed as structured field refs and are safely derivable from the aggregate join graph, for example left `tenantId` is joined to right `tenantId` and the right key is part of the aggregate relation grain. Raw SQL guards and implicit tenant conditions are intentionally not parsed or guessed.
- Query-time RHS pushdown is intentionally AND-only in this cut. OR groups, unsupported operators, and non-join-key left filters remain outer-query only.
- Query-time duplicated RHS fragments are rendered as generated SQL literals for the same reason as fixed filters; the outer QueryModel condition remains parameterized.
- System slice propagation through the normal QueryFacade lifecycle is covered in this cut. AccessBuilder structured field-ref join-key pushdown is now covered. Request-side `fieldAccess` allow/deny checks now cover aggregate relation output fields. System-slice guard fields may intentionally bypass user `fieldAccess` and must not leak into returned columns; authorization of the system-slice producer remains an upstream governance boundary. RHS raw-SQL guard pushdown remains a follow-up risk.
- Aggregate relation output fields now inherit TM measure captions and runtime types where safe. `COUNT` / `COUNT_DISTINCT` outputs are exposed as `BIGINT`; source-backed aggregates keep the source measure type and formatter. Output `extData.aggregateRelation` records aggregation, source column/caption/measure, semantic scale/unit metadata, and source/aggregate SQL lineage.
- SQLite targeted execution and MySQL 5.7 real database execution-plan evidence are recorded here. PostgreSQL and target TMS database evidence remain follow-up items because local Docker is unavailable and the local PostgreSQL port is closed.
- MCP/LLM public schema is not expanded. LLM analysis benefits from consuming stable QM fields after model authors define them, not from generating this DSL directly.

### Next Optimization: Query-Time RHS Filter Pushdown

Initial optimization behavior:

| Condition Source | Example | Current SQL Phase | Remaining Notes |
|---|---|---|---|
| Aggregate relation fixed filter | `sales.filterEq(sales.orderStatus, 'COMPLETED')` before `groupBy` | RHS derived table `WHERE` before `GROUP BY` | This remains the preferred way to express source-row filters. |
| Request slice on left join key | `slice: orderId = ...` | outer query `WHERE` plus mirrored RHS source-key `WHERE` | Covers the common TMS pattern where the left filter restricts the RHS aggregation key domain. |
| Request slice on aggregate relation group key | `slice: fs.orderId = ...` or aggregate relation field | outer query `WHERE` plus duplicated RHS source-key `WHERE` when resolved to aggregate relation output | Retains outer semantics; direct fixture exposure is limited by current field alias collision patterns. |
| Request slice on aggregate relation measure | `slice: salesAmount > 1000` | outer query `WHERE fsByOrder.salesAmount > ?` plus duplicated RHS `HAVING sum(...) > 1000` | Retaining the outer filter preserves LEFT JOIN no-match behavior. |
| Request slice on non-group RHS source field | `slice: orderStatus = ...` | not a stable public field unless modeled | Use relation fixed filters or an explicit RHS pre-aggregate filter contract. |
| OR / complex condition groups | `A or B` | outer query only | Not pushed in this cut to avoid changing boolean semantics. |

Optimization sequence status:

1. SQL-shape tests for aggregate relation group-key condition pushdown, aggregate measure slice, left join-key key-domain pushdown, and LEFT no-match behavior: done-initial.
2. Generated aggregate relation output metadata for group key, aggregate measure, TM caption/type inheritance, source semantic metadata, source column lineage, source expression, and aggregate expression: done.
3. Conservative condition-phase splitter: done-initial for AND-only aggregate relation fields and join-key mirroring.
4. Key-domain pushdown for common TMS left-filter-to-right-join-key cases: done-initial for safe operators supported by the generated aggregate relation renderer, including structured accessBuilder field-ref guards.
5. SQLite SQL-shape evidence and MySQL 5.7 real database `EXPLAIN` evidence: done-initial. PostgreSQL or target TMS database `EXPLAIN` evidence remains follow-up.

### TMS Feedback Hardening

2026-05-27 TMS feedback identified three follow-up points after the first aggregate relation smoke test.

| Feedback | Java engine status | Boundary |
|---|---|---|
| RHS tenant / access guard / join-key pushdown should be stronger | done-initial | Structured field-ref conditions added through `JdbcQuery.and/andIn/andNe/andNull/andNotNull` now attempt safe aggregate RHS pushdown. Left-side join-key guards are mirrored to RHS source-key `WHERE`; aggregate output guards route to RHS `WHERE` or `HAVING`. Tenant pushdown requires tenant to be an explicit aggregate join key/group key. |
| Aggregate relation field metadata should inherit TM measure semantics | done-initial | Runtime aggregate output columns now expose TM caption, resolved output type, formatter, AI/deprecation metadata, and `extData.aggregateRelation` lineage with aggregation/source/semantic scale/unit fields. |
| `frontend-meta` / schema aggregate field exposure should be checked | core-engine-covered, upstream-follow-up | Core QueryColumn schema now exposes inherited caption/type in tests. Query-cloud/data-viewer `frontend-meta` propagation remains a separate metadata-chain follow-up outside this Java engine cut. |

### Deferred Planning: ETL Promotion Boundary

ETL promotion is intentionally deferred. The current work stays on the Java engine aggregate join path, with real data tests and optimizer evidence. When the semantic path is stable, a separate work item can define the ETL promotion boundary:

1. Add evidence capture for representative TMS aggregate joins on the target old database family: generated SQL, `EXPLAIN`, scanned rows if available, and p95 latency.
2. Mark representative QMs with a promotion assessment: `runtimeAggregate`, `etlCandidate`, or `materializedAggregate`.
3. Define the first canonical TMS aggregate candidates by stable grain, for example order, waybill, inventory, plan, settlement, tenant, site, date, and status.
4. Keep runtime aggregate join as the development fallback while ETL freshness, backfill, and lineage policies are not yet defined.
5. Once a materialized aggregate exists, route the QM field to the materialized source while preserving the same semantic field contract for LLM analysis.
6. Record any query that still requires runtime aggregation on a legacy database as an explicit residual performance risk unless `EXPLAIN` evidence proves the database applies selective filtering before aggregation.

## Progress Tracking

### Development Progress

| Stage | Status | Notes |
|---|---|---|
| Stage 1 Contract and Parser Cut | done | Added `leftJoinAggregate` compatibility API plus aggregate relation proxy DSL; parser routes aggregate right relations before normal `JoinBuilder`. |
| Stage 2 Planning and SQL Lowering | done-initial | Added runtime synthetic aggregate relation with inline derived table lowering, fixed RHS slice pushdown, and TM metadata based default measures. |
| Stage 3 Field Exposure and Query Behavior | done-initial | Aggregate outputs are exposed as ordinary QM fields in both explicit-builder and relation-level fixtures; select and returnTotal paths are covered by query engine execution. |
| Stage 4 Tests and Evidence | done-real-db-initial | Added SQLite execution parity, LEFT no-match, SQL-shape, fail-closed groupBy validation, relation-level default aggregation, query-time RHS pushdown SQL-shape, MySQL 5.7 real database execution, and ordinary multi-fact join regression. |
| Query-Time RHS Filter Pushdown | done-initial | Aggregate relation column metadata, measure `HAVING` duplication, group/source key `WHERE` duplication, left join-key key-domain pushdown, and structured accessBuilder field-ref guard pushdown are implemented for AND-only safe predicates. |
| Aggregate Metadata Inheritance | done-initial | Aggregate relation output columns inherit TM caption/type/formatter and expose source/aggregation/semantic lineage in `extData.aggregateRelation`; frontend-meta propagation remains upstream chain follow-up. |
| ETL Promotion Boundary | deferred-out-of-scope | User confirmed ETL is not the current task. Development uses runtime aggregate join for semantic validation; ETL promotion can be reopened after the Java path stabilizes. |

### Testing Progress

| Test Area | Required | Status |
|---|---:|---|
| Contract validation unit tests | yes | done-initial: groupBy missing right join key fails closed. |
| Real query parity test | yes | done-initial: aggregate join result equals native `fact_sales` aggregate for a fixed order. |
| LEFT JOIN no-match behavior | yes | done-initial: left row is retained and RHS aggregate fields are null. |
| Permission/system slice propagation | yes | covered-initial: system slice lifecycle through QueryFacade is covered; structured accessBuilder field-ref join-key guard pushdown is covered; request-side `fieldAccess` allow/deny checks cover aggregate relation output fields; system-slice guard fields can filter without being exposed to user-visible columns. RHS raw-SQL guard pushdown remains a follow-up risk. |
| Dialect fallback evidence | yes | done-initial: SQLite and live MySQL 5.7 profile passed; PostgreSQL and target TMS database `EXPLAIN` remain follow-up because local Docker is unavailable and local PostgreSQL is closed. |
| Query-time RHS pushdown SQL shape | yes | done-initial: aggregate group-key condition duplicates to RHS `WHERE`; aggregate measure slice duplicates to RHS `HAVING`; left join-key slice and accessBuilder field-ref guards duplicate to RHS source-key `WHERE`; outer filters remain parameterized. |
| Query-time LEFT no-match semantics | yes | done-initial: measure slice with no RHS match returns zero rows because the outer filter is retained. |
| Ordinary join regression | yes | done: `MultiFactTableJoinTest` passed. |
| `mvn -pl foggy-dataset-model test` or targeted equivalent | yes | done-targeted. |

### Verification Evidence

| Command | Result |
|---|---|
| `mvn install -pl foggy-dataset-demo -DskipTests` | success; required because model tests load demo resources from the installed demo bundle. |
| `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db -Dtest=AggregateJoinQueryModelTest` | success; Tests run: 15, Failures: 0, Errors: 0, Skipped: 0. |
| `docker --version` | unavailable in this environment: `command not found`; local container startup could not be used for additional dialect services. |
| `nc -z 127.0.0.1 13306`, `nc -z 127.0.0.1 15432`, `nc -z 127.0.0.1 13308` | MySQL 5.7 port `13306` reachable; PostgreSQL `15432` and MySQL 8 `13308` closed. |
| `mvn test -pl foggy-dataset-model -Dspring.profiles.active=docker -P!multi-db -Dtest=AggregateJoinQueryModelTest` | success on live MySQL 5.7; Tests run: 15, Failures: 0, Errors: 0, Skipped: 0. |
| MySQL 5.7 `EXPLAIN` for aggregate relation with pushed filters | evidence shows derived aggregate source `agg_src` uses `uk_order_line` with `type=ref`, `rows=10`, and `Using where` for the pushed `order_id`, avoiding broad full-table aggregation for the tested selective predicate. |
| `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db -Dtest=MultiFactTableJoinTest` | success; Tests run: 13, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationOutputFieldShouldRespectFieldAccessAllowList+aggregateRelationOutputFieldShouldFailClosedWhenMissingFromFieldAccess' test` | success; aggregate relation output field `fieldAccess` allow/deny coverage; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationSystemSliceShouldBypassUserFieldAccessForGuardFields' test` | success; aggregate relation `system_slice` guard field bypasses user `fieldAccess` for filtering without leaking the guard field to returned columns; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=AggregateJoinQueryModelTest test` | success; full aggregate join sqlite regression after fieldAccess and system_slice guard coverage; Tests run: 34, Failures: 0, Errors: 0, Skipped: 0. |

### Experience Progress

experience: N/A.

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex
- signed_off_at: 2026-05-27
- acceptance_record: `docs/9.2.0/acceptance/query-model-aggregate-join-acceptance.md`
- blocking_items: none
- follow_up_required: yes

Reason: This work is Java engine behavior and semantic contract only; no UI interaction is changed in this implementation cut.

## Self-Check / Gate Requirements

- Implementation self-check is required before marking coding complete.
- Formal `foggy-implementation-quality-gate` is required because this is shared Java engine behavior.
- `foggy-test-coverage-audit` is required before acceptance because the change affects query correctness and regression risk.
- `foggy-acceptance-signoff` is required for final signoff.
