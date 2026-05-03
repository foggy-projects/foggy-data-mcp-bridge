---
doc_role: detailed_design
doc_purpose: Implementation plan and test matrix for Stage 5B PIVOT-91-C2 Cascade Generate
version: 9.1.0
target: Pivot Semantic Layer
status: signed-off-with-risks
created_at: 2026-05-02
---

# Pivot Stage 5B: C2 Implementation Plan

## 1. Purpose and Non-Goals

**Purpose**: Convert the accepted `PIVOT-91-C1.1` LLM-safe disambiguation rules into an executable Java implementation plan for `PIVOT-91-C2`. This plan defines the strict execution boundary, code touchpoints, error models, and exact test matrix required.

**Non-Goals**: No public Pivot DSL changes. No MDX-compatible set algebra. No queryModel bypass. No modification of the queryModel lifecycle.

## 2. Accepted Semantic Baseline

Based on C1 and C1.1, the C2 implementation must enforce:

- **Ranking Grain**: Current-level aggregate after parent-domain filtering.
- **Having Order**: Having before TopN at the same level.
- **Parent Isolation**: Child ranking occurs only within the surviving parent domain. Parent survival is not affected by child having/limit.
- **Axis Isolation**: Row and column ranking are independent until final assembly.
- **Subtotal/GrandTotal**: Aggregates over the surviving domain only.
- **Memory Fallback**: No current memory fallback is permitted for cascade requests.

## 3. C2 v1 Execution Pipeline

1. **Cascade Detection**: Identify requests with multi-level axis `limit` or `having`, or `limit` requiring parent-key partitioned ranking.
2. **Validation & Classification**: Ensure the request falls within the C2 v1 whitelist (rows two-level cascade, explicit `orderBy`, additive metrics only, supported dialect). Fail-closed with structured errors otherwise.
3. **Staged SQL Domain Generation**: Generate staged CTEs `_row_domain_1`, `_row_ranked_1`, `_filtered_1`, etc.
4. **Surviving-Domain Application**: Join the base relation with final surviving domains.
5. **Additive Subtotal/GrandTotal**: Compute additive totals over the Cartesian product of surviving row/column domains.
6. **No-Memory-Fallback Guard (`PivotPipeline`)**:
   - Existing `PivotPipeline` SQL pushdown unsupported flow may fallback to memory for non-cascade requests.
   - For cascade requests, any planner unsupported/failure must be converted to `PIVOT_CASCADE_SQL_REQUIRED`.
   - Cascade must never enter the current memory execution path.
   - Telemetry/logging should distinguish normal SQL fallback from cascade fail-closed refusal.
7. **Telemetry**: Log pushdown success, dialect capability refusal, or other fail-closed events.

## 4. Code Touchpoint Inventory

- `PivotPipeline.java`: Main orchestration. Needs to identify cascade semantics and delegate to the appropriate SQL planner. Must prevent fallback to memory executor for cascade.
- `PivotAxisDomainSqlPlanner.java` / `PivotTopNSqlPlanner.java`: Will need logic for staged CTE generation (e.g., `_level1_domain`, `_level2_domain`). Tie-breaking with NULL bucket must be implemented.
- `NonAdditiveRollupExecutor.java`: Ensure non-additive metrics with cascade ranking/having/totals are firmly rejected.

## 5. Error Model

The engine must fail closed with these rewriteable error categories:

- `PIVOT_CASCADE_ORDER_BY_REQUIRED`: A cascade level has `limit > 0` without explicit `orderBy`.
- `PIVOT_CASCADE_SQL_REQUIRED`: Staged SQL execution is unavailable or planner generation fails.
- `PIVOT_CASCADE_NON_ADDITIVE_REJECTED`: Non-additive metrics participate in cascade ranking/having/totals.
- `PIVOT_CASCADE_CROSS_AXIS_REJECTED`: Both row and column axes require cascade, or row cascade plus column TopN.
- `PIVOT_CASCADE_TREE_REJECTED`: `hierarchyMode=tree` is used with cascade.
- `PIVOT_CASCADE_SCOPE_UNSUPPORTED`: Request is outside C2 v1 scope (e.g., three-level cascade, multi-level having-only).

## 6. Oracle / Refusal Test Matrix

| Oracle Case | Target Test Class | Proposed Method | Profiles | Real SQL Data Parity | Expected Behavior | Error Code (if refusal) |
|---|---|---|---|---|---|---|
| 1. Parent TopN + child TopN | `PivotCascadeGenerateSqlParityIntegrationTest` | `testRowsTwoLevelCascadeSubset` | SQLite, MySQL8, PostgreSQL | yes | PASS: child domain is subset of parent | N/A |
| 2. Parent ranking by full aggregate | `PivotCascadeGenerateSqlParityIntegrationTest` | `testParentRankingIgnoresChildLimit` | SQLite, MySQL8, PostgreSQL | yes | PASS: parent rank unchanged by child limit | N/A |
| 3. Parent having + child TopN | `PivotCascadeGenerateSqlParityIntegrationTest` | `testParentHavingFiltersBeforeChildRank` | SQLite, MySQL8, PostgreSQL | yes | PASS: parent having filters before child ranking | N/A |
| 4. Child having + child TopN | `PivotCascadeGenerateSqlParityIntegrationTest` | `testChildHavingDoesNotAffectParent` | SQLite, MySQL8, PostgreSQL | yes | PASS: child having does not remove parent | N/A |
| 5. Missing `orderBy` on limited level | `PivotCascadeGenerateValidationTest` | `testMissingOrderByRejected` | SQLite | no | REJECT: refused before SQL generation | `PIVOT_CASCADE_ORDER_BY_REQUIRED` |
| 6. Deterministic tie with NULL keys | `PivotCascadeGenerateSqlParityIntegrationTest` | `testDeterministicTieWithNullBuckets` | SQLite, MySQL8, PostgreSQL | yes | PASS: stable ordering via NULL bucket + prefix key | N/A |
| 7. Additive row total over surviving domain | `PivotCascadeGenerateSqlParityIntegrationTest` | `testAdditiveRowSubtotalSurvivingDomain` | SQLite, MySQL8, PostgreSQL | yes | PASS: totals reflect only surviving cells | N/A |
| 8. Unsupported dialect/capability | `PivotCascadeGenerateValidationTest` | `testMysql57RefusedNoFallback` | MySQL5.7 (simulated) | no | REJECT: refused, no memory fallback | `PIVOT_CASCADE_SQL_REQUIRED` |
| 9. Non-additive ranking/having/totals | `PivotCascadeGenerateValidationTest` | `testNonAdditiveCascadeRejected` | SQLite | no | REJECT: refused before execution | `PIVOT_CASCADE_NON_ADDITIVE_REJECTED` |
| 10. Tree mode + cascade | `PivotCascadeGenerateValidationTest` | `testTreeModeCascadeRejected` | SQLite | no | REJECT: refused before execution | `PIVOT_CASCADE_TREE_REJECTED` |

## 7. Implementation Phases

- **Phase 1: Validation/Error Model**:
  - cascade detector implemented
  - unsupported shapes rejected before SQL generation
  - exact error code tests pass (`PivotCascadeGenerateValidationTest`)
  - no production SQL planner changes required to pass refusal tests

- **Phase 2: Staged SQL Planner**:
  - rows two-level staged CTE SQL generated (`PivotAxisDomainSqlPlannerCascadeTest`)
  - parent domain filter applied before child aggregation
  - explicit NULL bucket and prefix-key tie-breakers generated
  - SQL shape unit tests pass

- **Phase 3: Additive Totals**:
  - additive row subtotal and grandTotal computed over surviving domain
  - non-additive totals rejected
  - parity tests added for supported dialect profiles

- **Phase 4: Dialect Parity/Refusal**:
  - SQLite/MySQL8/PostgreSQL pass real SQL parity (`PivotCascadeGenerateSqlParityIntegrationTest`)
  - MySQL5.7 refusal test proves no memory fallback
  - SQL Server v1 deferred/refused
  - unsupported dialect produces `PIVOT_CASCADE_SQL_REQUIRED` (`PivotPipelineCascadeFallbackTest`)

- **Phase 5: Quality/Coverage/Acceptance**:
  - `git diff --check`
  - targeted Maven commands documented
  - quality gate / coverage audit / acceptance signoff required before marking C2 complete

## 8. Explicit Start Gate

Java implementation may start **only after** this plan is reviewed. C2 code must not start until oracle/refusal tests are mapped to concrete test classes and implementation approval is explicit.

Historical note: this start gate was satisfied before the C2 implementation. Current 9.1.0 status is signed off with risks for the C2 v1 rows two-level cascade subset.
