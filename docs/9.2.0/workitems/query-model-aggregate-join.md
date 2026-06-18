---
doc_role: workitem
doc_purpose: Track the Java QueryModel aggregate join implementation for stable semantic projections that must pre-aggregate one-to-many detail before joining.
version: 9.2.0
target: QueryModel Aggregate Join
status: accepted-with-risks
created_at: 2026-05-27
updated_at: 2026-06-18
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
- Request-side dynamic `calculatedFields` and QM predefined calculated fields that depend on aggregate relation outputs must inherit the same source physical-column permission boundary as the referenced aggregate output, including transitive calculated-field chains.

### Stage 4. Tests and Evidence

- Fixture: one left row with stock/count value 100 and three RHS details.
- Assert left-side measure remains 100 after aggregate join.
- Assert RHS aggregate equals the sum/count of the three details.
- Cover RHS fixed status slice.
- Cover tenant/system slice propagation.
- Cover invalid groupBy/join key mismatch refusal.
- Cover `orderBy` aggregate field and `returnTotal`.
- Cover `deniedColumns` for aggregate relation outputs, request-side dynamic calculated fields, and QM predefined calculated fields that reference those outputs.
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
- RHS fixed filter、运行时 filter、left join key pushdown、measure HAVING pushdown 均通过 derived relation 参数通道渲染为 `?`，由 `QueryObject.getBodyParameters()` 暴露绑定值，并在 JDBC visitor 中按 derived body before outer condition 的顺序合并参数。
- 当前 aggregate relation 仍要求 RHS `groupBy`，无 `groupBy` 的 RHS relation 是否支持需要单独语义设计或由上游测试确认后再扩展。

Data-viewer 入口要求：

- `/jdbc-model/query-model/v2/{model}` 已可通过 `param.extData` 进入 `ModelResultContext.extData`。
- `/data-viewer/api/query/direct/{qmModel}` 与 `/data-viewer/api/query/{model}/{queryId}/data` 需要将 `ViewerQueryRequest.extData` 透传到 `DbQueryRequestDef.extData`。
- 透传边界仅为 request -> `DbQueryRequestDef.extData` -> `ModelResultContext.extData`，不得自动转换为 `slice` / `where`。
- `query/create` 缓存链路如携带 `payload.extData`，应保留在 cached query context；执行时请求级 `extData` 可覆盖或合并缓存值。

Known limits in this cut:

- RHS fixed filters, runtime filters, and safe duplicated pushdown fragments are rendered with bind placeholders through the derived-query parameter channel; scalar/null/list validation remains fail-closed before SQL rendering.
- Relation-level default aggregation now prunes RHS derived relation projection to group keys plus aggregate outputs referenced by the current query `select` / `orderBy` / `groupBy` / structured field-ref conditions. If raw SQL conditions are added through `JdbcQuery.andSql` / `andSqlList`, pruning is disabled and the RHS projection falls back to the full generated output set because raw SQL aliases are intentionally not parsed.
- Query-time request slice now has a conservative RHS pushdown path: direct aggregate relation group-key filters are duplicated into RHS `WHERE`, aggregate measure filters are duplicated into RHS `HAVING`, and left join-key filters are mirrored into the RHS source key domain. Structured accessBuilder field-ref guards, for example `context.query.and(fo.orderId, ...)`, use the same safe join-key pushdown path. The outer QueryModel filter is retained to preserve LEFT JOIN result semantics.
- Aggregate relation group keys can be exposed under an explicit QM `name` / `alias` to avoid root-field collisions, for example `fsByOrder.orderId` exposed as `salesOrderId`. `SelectColumnDef.name` is treated as the QM external field identifier, while owner-model resolution for dimension expansion is based on the underlying `selectColumn` / `QueryObject` so aliases do not break join path resolution.
- Tenant/access guards can be pushed only when they are expressed as structured field refs and are safely derivable from the aggregate join graph, for example left `tenantId` is joined to right `tenantId` and the right key is part of the aggregate relation grain. Left-side join-key matching also recognizes equivalent visible TM/QM columns backed by the same physical SQL column, including no-table dimension `$id` refs such as `tenant$id` and property aliases such as `tenantId`. Raw SQL guards and implicit tenant conditions are intentionally not parsed or guessed.
- Query-time RHS pushdown is intentionally AND-only in this cut. OR groups, unsupported operators, and non-join-key left filters remain outer-query only; OR join-key and OR aggregate-measure request slices now have explicit regression coverage proving they are not copied into RHS `WHERE` / `HAVING`.
- Query-time duplicated RHS fragments now carry bind parameters inside the derived relation; the outer QueryModel condition is still retained and parameterized.
- Aggregate relation query objects now expose structured pushdown diagnostics for planner evidence. Pushed filters record `decision=pushed`, target phase (`where` / `having`), source field, operator, and rendered RHS expression. Refused or retained filters record stable reason codes such as `OR_CONDITION_OUTER_ONLY`, `NULL_CHECK_OUTER_ONLY`, `UNSUPPORTED_OPERATOR`, `EMPTY_IN_VALUES`, `INVALID_RANGE_VALUE`, `NULL_VALUE_UNSUPPORTED`, `NO_AGGREGATE_EXPRESSION`, and `NO_JOIN_KEY_MAPPING`. Semantic query responses surface these diagnostics under `debug.extra.aggregateRelationDiagnostics` when present, so SQL string assertions are not the only visibility into pushdown decisions.
- The diagnostics JSON contract is now pinned by a dedicated unit test for the six record keys `decision`, `reasonCode`, `field`, `op`, `target`, and `expression`, the pushed/retained/refused factory semantics, and the eight stable reason codes. This prevents AI/MCP consumers from silently losing planner evidence after refactors.
- System slice propagation through the normal QueryFacade lifecycle is covered in this cut. AccessBuilder structured field-ref join-key pushdown is now covered. Request-side `fieldAccess` allow/deny checks and source physical-column `deniedColumns` now cover aggregate relation output fields plus calculated fields that depend on them, including QM predefined calculated fields. System-slice guard fields may intentionally bypass user `fieldAccess` and must not leak into returned columns; authorization of the system-slice producer remains an upstream governance boundary. Raw SQL accessBuilder guards are intentionally not parsed or guessed into RHS pushdown; they remain parameterized outer filters.
- Aggregate relation output fields now inherit TM measure captions and runtime types where safe. `COUNT` / `COUNT_DISTINCT` outputs are exposed as `BIGINT`; source-backed aggregates keep the source measure type and formatter. Output `extData.aggregateRelation` records aggregation, source column/caption/measure, semantic scale/unit metadata, and source/aggregate SQL lineage.
- Aggregate relation output fields are now explicitly covered when referenced by top-level `orderBy`; projection pruning keeps the required RHS aggregate measure, and the outer `ORDER BY` uses the aggregate relation output alias. QueryFacade `returnTotal` is also covered for aggregate relation requests, including the total SQL retaining the aggregate relation derived table.
- SQLite targeted execution, local TMS-style order+site composite-key fixture execution, MySQL 5.7 real database execution-plan evidence, PostgreSQL 15 targeted aggregate-join evidence, and the full PostgreSQL dataset-model gate are recorded here. Real target TMS database evidence remains a follow-up item.
- The Java aggregate join parity snapshot exporter is explicit opt-in. `JavaQueryModelAggregateJoinSnapshotTest` skips by default and writes `target/parity/_querymodel_aggregate_join_snapshot.json` only when `-Dfoggy.parity.snapshot=true` is supplied. This keeps ordinary Maven test runs from refreshing generated parity evidence accidentally.
- MCP/LLM public schema is not expanded. LLM analysis benefits from consuming stable QM fields after model authors define them, not from generating this DSL directly.

### Next Optimization: Query-Time RHS Filter Pushdown

Initial optimization behavior:

| Condition Source | Example | Current SQL Phase | Remaining Notes |
|---|---|---|---|
| Aggregate relation fixed filter | `sales.filterEq(sales.orderStatus, 'COMPLETED')` before `groupBy` | RHS derived table `WHERE` before `GROUP BY` | This remains the preferred way to express source-row filters. |
| Request slice on left join key | `slice: orderId = ...` | outer query `WHERE` plus mirrored RHS source-key `WHERE` | Covers the common TMS pattern where the left filter restricts the RHS aggregation key domain. |
| Request slice on aggregate relation group key | `slice: salesOrderId = ...` where `salesOrderId` exposes `fsByOrder.orderId` | outer query `WHERE` plus duplicated RHS source-key `WHERE` when resolved to aggregate relation output | Retains outer semantics; explicit QM alias fixture now covers the request-time path without colliding with root `orderId`. |
| Request slice on aggregate relation measure | `slice: salesAmount > 1000` | outer query `WHERE fsByOrder.salesAmount > ?` plus duplicated RHS `HAVING sum(...) > 1000` | Retaining the outer filter preserves LEFT JOIN no-match behavior. |
| Request slice on non-group RHS source field | `slice: orderStatus = ...` | not a stable public field unless modeled | Use relation fixed filters or an explicit RHS pre-aggregate filter contract. |
| OR / complex condition groups | `A or B` | outer query only | Not pushed in this cut to avoid changing boolean semantics; join-key OR, aggregate-measure OR, and mixed join-key/measure OR are covered as outer-only regressions. AND `in`/range groups are covered for safe RHS `WHERE` / `HAVING` duplication. |

Optimization sequence status:

1. SQL-shape tests for aggregate relation group-key condition pushdown, aggregate measure slice, left join-key key-domain pushdown, and LEFT no-match behavior: done-initial.
2. Generated aggregate relation output metadata for group key, aggregate measure, TM caption/type inheritance, source semantic metadata, source column lineage, source expression, and aggregate expression: done.
3. Conservative condition-phase splitter: done-initial for AND-only aggregate relation fields and join-key mirroring.
4. Key-domain pushdown for common TMS left-filter-to-right-join-key cases: done-initial for safe operators supported by the generated aggregate relation renderer, including structured accessBuilder field-ref guards.
5. SQLite SQL-shape evidence, local TMS-style composite-key fixture evidence, MySQL 5.7 real database `EXPLAIN` evidence, PostgreSQL 15 targeted aggregate-join evidence, and the full PostgreSQL dataset-model gate: done-initial. Real target TMS database `EXPLAIN` evidence remains follow-up.

### TMS Feedback Hardening

2026-05-27 TMS feedback identified three follow-up points after the first aggregate relation smoke test.

| Feedback | Java engine status | Boundary |
|---|---|---|
| RHS tenant / access guard / join-key pushdown should be stronger | done-hardening | Structured field-ref conditions added through `JdbcQuery.and/andIn/andNe/andNull/andNotNull` now attempt safe aggregate RHS pushdown. Left-side join-key guards are mirrored to RHS source-key `WHERE`; aggregate output guards route to RHS `WHERE` or `HAVING`. Tenant pushdown requires tenant to be an explicit aggregate join key/group key, and the engine now resolves same-physical-column left refs such as `tenantId` / `tenant$id` before deciding whether a guard can be mirrored. |
| Aggregate relation field metadata should inherit TM measure semantics | done-initial | Runtime aggregate output columns now expose TM caption, resolved output type, formatter, AI/deprecation metadata, and `extData.aggregateRelation` lineage with aggregation/source/semantic scale/unit fields. |
| `frontend-meta` / schema aggregate field exposure should be checked | core-engine-covered, upstream-follow-up | Core QueryColumn schema and Java V3 JSON metadata now expose aggregate relation measures with inherited caption/type/aggregation, model attribution, and structured `aggregateRelation` lineage. Query-cloud/data-viewer consumption and display remain separate metadata-chain follow-ups outside this Java engine cut. |

### Deferred Planning: ETL Promotion Boundary

ETL promotion is intentionally deferred. The current work stays on the Java engine aggregate join path, with real data tests and optimizer evidence. When the semantic path is stable, a separate work item can define the ETL promotion boundary:

1. Add evidence capture for representative TMS aggregate joins on the target old database family: generated SQL, `EXPLAIN`, scanned rows if available, and p95 latency.
2. Mark representative QMs with a promotion assessment: `runtimeAggregate`, `etlCandidate`, or `materializedAggregate`.
3. Define the first canonical TMS aggregate candidates by stable grain, for example order, waybill, inventory, plan, settlement, tenant, site, date, and status.
4. Keep runtime aggregate join as the development fallback while ETL freshness, backfill, and lineage policies are not yet defined.
5. Once a materialized aggregate exists, route the QM field to the materialized source while preserving the same semantic field contract for LLM analysis.
6. Record any query that still requires runtime aggregation on a legacy database as an explicit residual performance risk unless `EXPLAIN` evidence proves the database applies selective filtering before aggregation.

## AI/MCP Diagnostic Consumption Contract

Aggregate relation diagnostics are exposed at
`SemanticQueryResponse.debug.extra.aggregateRelationDiagnostics` and are
preserved through the Java MCP natural-language result capture path when
present. The field is optional and diagnostic-only; result correctness still
comes from `items`, `schema`, `total`, and retained outer predicates.

Consumers should interpret each diagnostic item as planner evidence:

| Field | Meaning |
|---|---|
| `decision` | `pushed`, `retained`, or `refused`. |
| `target` | `where` / `having` for RHS aggregate duplicates, or `outer` for retained predicates. |
| `reasonCode` | Stable reason when the predicate is not pushed, including `OR_CONDITION_OUTER_ONLY`, `NULL_CHECK_OUTER_ONLY`, `UNSUPPORTED_OPERATOR`, `EMPTY_IN_VALUES`, `INVALID_RANGE_VALUE`, `NULL_VALUE_UNSUPPORTED`, `NO_AGGREGATE_EXPRESSION`, and `NO_JOIN_KEY_MAPPING`. |
| `field` / `op` | Semantic request field and operator that triggered the decision. |
| `expression` | SQL fragment evidence for pushed predicates only; it is for explanation/audit, not for client-side execution. |

AI answer synthesis may use `pushed` diagnostics to explain that selective
filters were duplicated into the aggregate RHS before aggregation. It must not
treat missing diagnostics as a failed query, and it must treat `retained` /
`refused` as a safe boundary rather than an engine error.
Regression coverage now pins both AI-facing semantic responses and the real MCP
`dataset.query_model` callback capture path, including retained/refused
diagnostics.
`AggregateRelationDiagnosticContractTest` additionally pins the serialized key
set and reason-code set directly, so downstream consumers can treat these
fields as a stable diagnostic contract.

## Progress Tracking

### Development Progress

| Stage | Status | Notes |
|---|---|---|
| Stage 1 Contract and Parser Cut | done | Added `leftJoinAggregate` compatibility API plus aggregate relation proxy DSL; parser routes aggregate right relations before normal `JoinBuilder`. |
| Stage 2 Planning and SQL Lowering | done-initial | Added runtime synthetic aggregate relation with inline derived table lowering, fixed RHS slice pushdown, TM metadata based default measures, and relation-level RHS projection pruning for tracked query references. |
| Stage 3 Field Exposure and Query Behavior | done-initial | Aggregate outputs are exposed as ordinary QM fields in both explicit-builder and relation-level fixtures; select, order/group reference marking, condition marking, and returnTotal paths are covered by query engine execution. |
| Stage 4 Tests and Evidence | done-real-db-initial | Added SQLite execution parity, LEFT no-match, SQL-shape, fail-closed groupBy validation, relation-level default aggregation/projection pruning, query-time RHS pushdown SQL-shape and OR outer-only boundary coverage, local TMS-style order+site composite-key fixture execution, MySQL 5.7 real database execution, and ordinary multi-fact join regression. |
| Query-Time RHS Filter Pushdown | done-hardening | Aggregate relation column metadata, measure `HAVING` duplication, group/source key `WHERE` duplication, left join-key key-domain pushdown, and structured accessBuilder field-ref guard pushdown are implemented for AND-only safe predicates. Pushdown/outer-only/refusal decisions now have structured diagnostics with stable reason codes. Left join-key matching now includes same-physical-column aliases across dimensions, properties, and measures, covering explicit tenant guard refs that use a no-table dimension `$id` while the aggregate relation join uses the property alias. |
| Aggregate Group-Key Alias Slice | done | Request-side `slice` on an explicitly exposed aggregate relation group key alias duplicates the filter into RHS source-key `WHERE`, keeps the outer aggregate relation filter, and returns the RHS group key under the QM alias without colliding with the root field. |
| Derived Relation Parameter Binding | done | `QueryObject.getBodyParameters()` lets aggregate relation derived SQL expose bind values; fixed/runtime filters and pushed RHS fragments render placeholders instead of generated literals. SQLite BigDecimal parameter conversion was normalized to numeric binding for aggregate comparisons. |
| Aggregate Metadata Inheritance | done-hardening | Aggregate relation output columns inherit TM caption/type/formatter and expose source/aggregation/semantic lineage in `extData.aggregateRelation`; Java V3 JSON metadata also exposes QM-only aggregate relation measures such as `salesAmount` with caption/type/aggregation/model attribution and structured `aggregateRelation` lineage. Query-cloud/data-viewer consumption remains upstream chain follow-up. |
| Aggregate Output Order/Total | done | Top-level `orderBy` on aggregate relation output measures retains the needed RHS projection and renders against the relation output alias. QueryFacade `returnTotal` executes total SQL with the aggregate relation derived table retained. |
| ETL Promotion Boundary | deferred-out-of-scope | User confirmed ETL is not the current task. Development uses runtime aggregate join for semantic validation; ETL promotion can be reopened after the Java path stabilizes. |

### Testing Progress

| Test Area | Required | Status |
|---|---:|---|
| Contract validation unit tests | yes | done-initial: groupBy missing right join key fails closed. |
| Real query parity test | yes | done-initial: aggregate join result equals native `fact_sales` aggregate for a fixed order. |
| LEFT JOIN no-match behavior | yes | done-initial: left row is retained and RHS aggregate fields are null. |
| Permission/system slice propagation | yes | covered-hardening: system slice lifecycle through QueryFacade is covered; structured accessBuilder field-ref join-key guard pushdown is covered; explicit tenant guard refs that resolve to an equivalent physical left join key are pushed into RHS aggregate `WHERE`; request-side `fieldAccess` allow/deny checks and source physical-column `deniedColumns` cover aggregate relation output fields and calculated fields that depend on them, including QM predefined calculated fields; system-slice guard fields can filter without being exposed to user-visible columns. Raw SQL accessBuilder guards are covered as outer-only/no-pushdown boundary. |
| Dialect fallback evidence | yes | done-initial: SQLite and live MySQL 5.7 profile passed; PostgreSQL targeted aggregate-join subset and full dataset-model gate passed on local Docker `postgres:15-alpine`. SQL Server evidence and real target TMS database `EXPLAIN` remain follow-up. |
| Query-time RHS pushdown SQL shape | yes | done-initial: aggregate group-key condition duplicates to RHS `WHERE`; aggregate measure slice duplicates to RHS `HAVING`; left join-key slice and accessBuilder field-ref guards duplicate to RHS source-key `WHERE`; outer filters remain parameterized. |
| Aggregate relation diagnostics JSON contract | yes | done: `AggregateRelationDiagnosticContractTest` pins the six JSON keys, pushed/retained/refused factory semantics, and all eight stable reason codes. |
| TMS-style composite-key representative fixture | yes | done-local-gate-defined: `TmsStyleOrderStoreSalesAggregateRelationQueryModel` uses order + site grain and verifies request slices duplicate to RHS `agg_src.order_id = ?`, `agg_src.store_key = ?`, and `HAVING sum(agg_src.sales_amount) > ?` while native aggregate parity holds. Registry promotion gate is refreshed in `foggy-model-registry` commit `881912a`; real TMS authority models and target database evidence remain required before package publication. |
| Aggregate group-key alias request slice | yes | done: explicit `salesOrderId` QM alias for `fsByOrder.orderId` is selected and sliced through the request path; RHS `agg_src.order_id = ?`, outer `fsByOrder.orderId = ?`, and stable parameter ordering are asserted. |
| Query-time OR / complex predicate boundary | yes | done-hardening: OR join-key slices, OR aggregate-measure slices, and mixed join-key/measure OR remain outer-query only and are not copied into RHS `WHERE` / `HAVING`; AND `in`/range slices duplicate safely to RHS source-key `WHERE` and aggregate-measure `HAVING` while retaining outer filters. |
| Derived relation parameter binding | yes | done: RHS fixed/runtime filters and duplicated pushdown fragments use `?` placeholders with body parameters merged ahead of outer filters; SQLite aggregate comparison regression covers numeric binding. |
| Query-time LEFT no-match semantics | yes | done-initial: measure slice with no RHS match returns zero rows because the outer filter is retained. |
| Relation-level RHS projection pruning | yes | done: unreferenced aggregate relation measures are omitted from RHS SELECT; raw SQL accessBuilder conditions disable pruning and retain the full RHS projection. |
| Aggregate output orderBy / returnTotal | yes | done: top-level orderBy on aggregate relation measure keeps RHS projection and executes; QueryFacade returnTotal returns the filtered total and totalData while preserving aggregate relation SQL. |
| Ordinary join regression | yes | done: `MultiFactTableJoinTest` passed. |
| `mvn -pl foggy-dataset-model test` or targeted equivalent | yes | done-targeted. |

### Verification Evidence

| Command | Result |
|---|---|
| `mvn install -pl foggy-dataset-demo -DskipTests` | success; required because model tests load demo resources from the installed demo bundle. |
| `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db -Dtest=AggregateJoinQueryModelTest` | success; Tests run: 15, Failures: 0, Errors: 0, Skipped: 0. |
| Earlier local Docker/port probes | Initial probes before compose services were available showed only MySQL 5.7 reachable; these are superseded for PostgreSQL by the 2026-06-18 local Docker evidence below. |
| `docker ps --format '{{.Names}} {{.Image}} {{.Ports}}' \| rg 'foggy-demo-postgres|foggy-demo-mysql8'` | Local Docker services are running: `foggy-demo-postgres postgres:15-alpine` exposes `15432:5432`, and `foggy-demo-mysql8 mysql:8.0` exposes `13308:3306`. |
| `docker exec foggy-demo-postgres pg_isready -U foggy -d foggy_test` | success; `/var/run/postgresql:5432 - accepting connections`. |
| `mvn test -pl foggy-dataset-model -Dspring.profiles.active=docker -P!multi-db -Dtest=AggregateJoinQueryModelTest` | success on live MySQL 5.7; Tests run: 15, Failures: 0, Errors: 0, Skipped: 0. |
| MySQL 5.7 `EXPLAIN` for aggregate relation with pushed filters | evidence shows derived aggregate source `agg_src` uses `uk_order_line` with `type=ref`, `rows=10`, and `Using where` for the pushed `order_id`, avoiding broad full-table aggregation for the tested selective predicate. |
| `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db -Dtest=MultiFactTableJoinTest` | success; Tests run: 13, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationOutputFieldShouldRespectFieldAccessAllowList+aggregateRelationOutputFieldShouldFailClosedWhenMissingFromFieldAccess' test` | success; aggregate relation output field `fieldAccess` allow/deny coverage; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationSystemSliceShouldBypassUserFieldAccessForGuardFields' test` | success; aggregate relation `system_slice` guard field bypasses user `fieldAccess` for filtering without leaking the guard field to returned columns; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationRawSqlAccessBuilderShouldStayOuterOnly' test` | success; raw SQL accessBuilder guard remains a parameterized outer filter and is not parsed or copied into RHS aggregate `WHERE`; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationShouldRenderDefaultMeasureAggregation+aggregateRelationRawSqlAccessBuilderShouldStayOuterOnly' test` | success; aggregate relation RHS projection prunes unreferenced measures for structured requests and falls back to full projection when raw SQL accessBuilder conditions are present; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=AggregateJoinQueryModelTest test` | success; full aggregate join sqlite regression after fieldAccess, system_slice guard, raw SQL no-pushdown boundary, and RHS projection pruning coverage; Tests run: 35, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationShouldRunExplainWithPushedRightSideFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; aggregate RHS fixed filter, join-key pushdown, and HAVING pushdown render as placeholders and bind values through derived body parameters; SQLite BigDecimal numeric parameter conversion keeps `SUM(real) > ?` executable; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; full aggregate join sqlite regression after derived relation parameter binding; Tests run: 35, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationOrJoinKeySliceShouldStayOuterOnly+aggregateRelationOrMeasureSliceShouldStayOuterOnly' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; OR join-key and OR aggregate-measure request slices remain outer-query only and are not copied into RHS aggregate `WHERE` / `HAVING`; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; full aggregate join sqlite regression after OR outer-only boundary coverage; Tests run: 37, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationO615ProbeExpressJoinNoColumnsShouldResolveJoinPath+aggregateRelationO615TenantGuardShouldBypassFieldAccessWithoutLeaking' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; O615 explicit tenant guard backed by an equivalent left physical column is pushed into RHS aggregate `WHERE`, RHS groupBy retains the tenant key, and system-slice tenant guard bypasses user fieldAccess without leaking to returned columns; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=AggregateJoinQueryModelTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; full aggregate join sqlite regression after equivalent left join-key pushdown hardening; Tests run: 38, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationMeasureOrderByShouldRetainProjection+aggregateRelationReturnTotalShouldKeepAggregateRelationQuery' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; aggregate relation measure `orderBy` keeps RHS projection and executes, while QueryFacade `returnTotal` returns filtered total/totalData and keeps aggregate relation SQL; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=AggregateJoinQueryModelTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; full aggregate join sqlite regression after aggregate output `orderBy` and QueryFacade `returnTotal` coverage; Tests run: 40, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationGroupKeyAliasSliceShouldPushWhereThroughRequest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; request slice on aggregate relation group-key alias `salesOrderId` duplicates to RHS `agg_src.order_id = ?`, keeps the outer relation filter, returns `salesOrderId`, and preserves parameter order; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationOutputFieldShouldFailClosedWhenDeniedPhysicalSourceColumn+aggregateRelationDeniedPhysicalUnreferencedSourceColumnShouldPass' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; aggregate relation output `salesAmount` is denied through RHS source physical column `fact_sales.sales_amount`, while unrelated denied source column `profit_amount` does not over-block; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest,PhysicalColumnPermissionIntegrationTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; aggregate relation suite and physical-column permission integration suite pass together after aggregate relation source physical-column mapping hardening; Tests run: 54, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationO615ProbeExpressJoinNoColumnsShouldResolveJoinPath+aggregateRelationO615ProbeExpressJoinDimensionIdSliceShouldResolveJoinPath' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; explicit QM external aliases no longer break dimension owner resolution or O615 join-path planning after owner lookup was changed to prefer the underlying selected column; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=AggregateJoinQueryModelTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; full aggregate join sqlite regression after aggregate group-key alias slice and QM owner-resolution hardening; Tests run: 41, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=MultiFactTableJoinTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; ordinary multi-fact join regression after QM alias owner-resolution hardening; Tests run: 13, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationCalculatedFieldShouldFailClosedWhenDeniedPhysicalSourceColumn+aggregateRelationCalculatedFieldChainShouldFailClosedWhenDeniedPhysicalSourceColumn+aggregateRelationPredefinedCalculatedFieldShouldFailClosedWhenDeniedPhysicalSourceColumn' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; request-side direct/chained dynamic calculated fields and QM predefined calculated field that depend on aggregate relation output `salesAmount` are rejected when RHS source physical column `fact_sales.sales_amount` is denied; Tests run: 3, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=JavaGovernanceSnapshotTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; governance snapshot covers request-side calculatedFields dependency expansion for denied physical columns; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationPredefinedCalculatedFieldShouldExecuteWhenAllowed' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; QM predefined calculated field `salesAmountPredefinedTax` executes against aggregate relation output `salesAmount` when the RHS source physical column is not denied; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=AggregateJoinQueryModelTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; full aggregate join sqlite regression after QM predefined calculated-field positive execution coverage; Tests run: 47, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationMixedOrSliceShouldStayOuterOnly+aggregateRelationAndInRangeSlicesShouldPushRightFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; mixed join-key/measure OR remains outer-only with `OR_CONDITION_OUTER_ONLY` diagnostics, while AND `in`/range slices duplicate to RHS source-key `WHERE` and aggregate-measure `HAVING` with pushed `where` / `having` diagnostics; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationPushdownRefusalShouldRecordReasonCodes+aggregateRelationOutputNullSliceShouldStayOuterWhere+aggregateRelationOutputNotNullSliceShouldStayOuterWhere' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; aggregate relation diagnostics now cover refused `UNSUPPORTED_OPERATOR`, `EMPTY_IN_VALUES`, `INVALID_RANGE_VALUE`, and retained `NULL_CHECK_OUTER_ONLY` for outer-only null checks; Tests run: 3, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#semanticResponseShouldExposeAggregateRelationDiagnostics' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; semantic response `debug.extra.aggregateRelationDiagnostics` exposes aggregate relation pushed `where` / `having` planner evidence for AI-facing query responses; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#semanticResponseShouldExposeRetainedAndRefusedAggregateRelationDiagnostics' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; semantic response `debug.extra.aggregateRelationDiagnostics` exposes retained `OR_CONDITION_OUTER_ONLY` and refused `INVALID_RANGE_VALUE` planner evidence for AI-facing query responses; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#tmsStyleAggregateRelationShouldPushCompositeKeyFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; local TMS-style order+site composite-key aggregate relation fixture verifies RHS source-key `WHERE`, measure `HAVING`, and native aggregate parity; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=AggregateJoinQueryModelTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; full aggregate join SQLite regression after mixed OR, AND `in`/range predicate-boundary, diagnostics reason-code, semantic debug.extra exposure, and TMS-style composite-key fixture coverage; Tests run: 52, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=docker -Dtest='AggregateJoinQueryModelTest#aggregateRelationShouldRunExplainWithPushedRightSideFilters+aggregateRelationAndInRangeSlicesShouldPushRightFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success on live MySQL 5.7; aggregate relation `EXPLAIN` shows derived source `agg_src` using `uk_order_line`, `type=ref`, `rows=2`, and `Using where` for pushed filters; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=docker -Dtest='AggregateJoinQueryModelTest#tmsStyleAggregateRelationShouldPushCompositeKeyFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success on live MySQL 5.7; local TMS-style composite-key aggregate relation executed with derived RHS filters on `order_id` and `store_key`; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=docker -Dtest='AggregateJoinQueryModelTest#semanticResponseShouldExposeAggregateRelationDiagnostics' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success on live MySQL 5.7; semantic response `debug.extra.aggregateRelationDiagnostics` exposes pushed aggregate relation `where` / `having` planner evidence through the MySQL quoting and `limit 100` path; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=postgres -Dtest='AggregateJoinQueryModelTest#aggregateRelationShouldRunExplainWithPushedRightSideFilters+aggregateRelationAndInRangeSlicesShouldPushRightFilters+tmsStyleAggregateRelationShouldPushCompositeKeyFilters+aggregateRelationOnLeftKeyShouldSupportJoinedDimensionField+aggregateRelationOnLeftKeyShouldSupportNestedDimensionPath+aggregateRelationRhsFixedFilterShouldSupportRightDimensionField' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success on live PostgreSQL `postgres:15-alpine`; aggregate relation EXPLAIN/pushdown, composite-key pushdown, joined-dimension left-key, nested dimension path, and RHS dimension fixed-filter subset passed; Tests run: 6, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model test-compile surefire:test@test-postgres -Dsurefire.failIfNoSpecifiedTests=false` | success on live PostgreSQL `postgres:15-alpine`; full dataset-model PostgreSQL gate after closing aggregate relation self-alias projection pruning, CTE running-sum post-slice, and YoY oracle-limit regressions; Tests run: 3162, Failures: 0, Errors: 0, Skipped: 3. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-mcp -am -P'!multi-db' -Dtest='QueryExpertServiceRoutingCalibrationTest#captureQueryResult_shouldNormalizeRxSemanticQueryResponse' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; Java MCP natural-language capture preserves `SemanticQueryResponse.debug.extra.aggregateRelationDiagnostics` while retaining normalized `items` / `total` / `hasNext`; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-mcp -am -P'!multi-db' -Dtest='McpToolCallbackFactoryTest#queryModelSuccessRx_shouldCaptureDebugExtraDiagnostics' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; real `dataset.query_model` callback captures successful `RX<SemanticQueryResponse>` results and preserves `debug.extra.aggregateRelationDiagnostics` in `QueryExpertService.LAST_QUERY_RESULT`; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='SemanticServiceV3Test#testMetadata_Json_ShouldExposeAggregateRelationMeasure' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; Java V3 JSON metadata exposes aggregate relation output measure `salesAmount` with inherited caption/type/aggregation, QueryModel attribution, and structured `aggregateRelation` lineage; the JSON contract now pins the seven string keys `aggregation`, `sourceCaption`, `sourceMeasure`, `sourceAlias`, `sourceExpression`, `aggregateExpression`, and `sourceColumn`; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=SemanticServiceV3Test -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; full V3 semantic metadata/query regression after aggregate relation JSON metadata lineage contract hardening; Tests run: 22, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dtest='AggregateRelationDiagnosticContractTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; aggregate relation diagnostics contract pins record component order, JSON keys, pushed/retained/refused factory semantics, and the eight reason codes; Tests run: 3, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='JavaQueryModelAggregateJoinSnapshotTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; aggregate join parity snapshot exporter is skipped by default unless `foggy.parity.snapshot` is enabled; Tests run: 1, Failures: 0, Errors: 0, Skipped: 1. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dfoggy.parity.snapshot=true -Dtest='JavaQueryModelAggregateJoinSnapshotTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; explicit aggregate join parity export writes `target/parity/_querymodel_aggregate_join_snapshot.json`; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |

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
