---
doc_role: workitem_index
doc_purpose: Track executable Java Pivot Engine follow-up items for 9.1.0.
version: 9.1.0
target: Java Pivot Engine Follow-Ups
status: phase-closeout signed-off-with-risks
created_at: 2026-05-02
---

# Pivot Java Engine 9.1.0 Follow-Ups

## Workitem Table

| ID | Priority | Item | Status | Owner | Exit Criteria |
|---|---|---|---|---|---|
| PIVOT-91-A1 | P0 | CI release readiness for Pivot Java Core | complete | Java Core / CI | SQLite PR gate and MySQL8/PostgreSQL release gate documented and runnable |
| PIVOT-91-A2 | P0 | Pushdown/fallback/domain-limit telemetry | complete | Java Core | Safe log markers cover pushdown success/fallback/skip, fallback reason, domain-limit exceptions, domain size, and non-additive auxiliary query count/duration |
| PIVOT-91-B1 | P1 | Stage 5A `DomainTransportPlan` design | complete | Java Core | Design accepted with dialect matrix and queryModel lifecycle preservation |
| PIVOT-91-B2 | P1 | Stage 5A production implementation | B2-prep / pending | Java Core | Skeleton merged, but production behavior is disabled |
| PIVOT-91-C1 | P1 | Cascade Generate semantic design | complete | Java Core / Product | Multi-level TopN semantics frozen with SQL oracle examples |
| PIVOT-91-C2 | P2 | Cascade Generate implementation | gated | Java Core | Only starts after C1 acceptance |
| PIVOT-91-D1 | P2 | Tree SQL TopN / tree subtotal feasibility | proposed | Java Core | Feasibility report with closure-table/descendant strategy decision |
| PIVOT-91-E1 | P2 | Outer Pivot cache feasibility | proposed | Java Core / Cache | Cache key and invalidation model accepted |

## Recommended Execution Order

1. PIVOT-91-A1: CI release readiness.
2. PIVOT-91-A2: telemetry.
3. PIVOT-91-B1: Stage 5A design.
4. Decide PIVOT-91-B2 based on telemetry or product need.
5. PIVOT-91-C1 before any advanced Generate implementation.
6. PIVOT-91-D1 and PIVOT-91-E1 after ranking semantics and telemetry are clearer.

## Guardrails

- No queryModel bypass.
- No public DSL changes without a separate requirement.
- No default-on Stage 5A implementation without cross-database parity.
- No Stage 5B implementation before semantics are accepted.
- Keep MySQL 5.7 behavior explicit: fallback, derived-table support, or unsupported must be documented per workitem.

## Progress Update - 2026-05-02

### PIVOT-91-A1 CI Release Readiness

Status: `complete`.

Implemented:

- `.github/workflows/pivot-release-readiness.yml` adds a SQLite PR-fast path for `PivotSqlParityIntegrationTest`.
- The same workflow provides a scheduled/manual MySQL8/PostgreSQL release gate backed by `foggy-dataset-demo/docker/docker-compose.yml`.
- `scripts/verify-pivot-v9-release.ps1` and `scripts/verify-pivot-v9-release.sh` wait for Docker container readiness before external DB parity.
- Targeted release-gate Maven commands now use `-am` plus `-Dsurefire.failIfNoSpecifiedTests=false` so local upstream module changes are compiled and tested in the same reactor.
- MySQL8 JDBC detection now returns a MySQL 8 capability profile with CTE/window support; conservative MySQL/MariaDB behavior remains fail-closed.

Evidence:

- `./scripts/verify-pivot-v9-release.ps1 -SkipFullRegression -SkipMcp` passed on 2026-05-02, covering SQLite, MySQL8, and PostgreSQL `PivotSqlParityIntegrationTest`.
- MySQL8 previously failed because the datasource was mapped to the conservative MySQL 5.7 dialect profile; after `Mysql8Dialect`/`DbUtils` correction, MySQL8 pushdown uses `WITH ... ROW_NUMBER()` and parity passes.
- `mvn test -pl foggy-dataset-model -am "-Dtest=PivotIntegrationTest" "-Dspring.profiles.active=sqlite" "-P!multi-db" "-Dsurefire.failIfNoSpecifiedTests=false"` passed.

Remaining:

- Full regression and MCP schema guardrail were intentionally skipped in the local evidence run to keep the milestone scoped; they remain part of the full release script when skip flags are not supplied.

### PIVOT-91-A2 Pushdown/Fallback/Domain-Limit Telemetry

Status: `complete`.

Implemented:

- `PivotTelemetry` centralizes safe structured log markers for:
  - `pivot.sql_pushdown.attempted`
  - `pivot.sql_pushdown.succeeded`
  - `pivot.sql_pushdown.fallback`
  - `pivot.sql_pushdown.skipped`
  - `pivot.non_additive.domain_limit_exceeded`
  - `pivot.non_additive.aux_query.started`
  - `pivot.non_additive.aux_query.completed`
  - `pivot.non_additive.aux_query.fallback`
- SQL pushdown success logs model, row count, duration, and `fallback=none`.
- SQL pushdown fallback logs sanitized reason class/message and `fallback=memory`.
- SQL pushdown skip logs a structured skip reason at debug level: disabled, queryFacade unavailable, tree mode, derived metric post-processing, or no axis having/limit.
- `domain > 500` non-additive rollup fail-closed logs domain size, max allowed size, model, whether SQL pushdown was used, and row/column domain sizes before raising the user-facing exception.
- Non-additive auxiliary rollup logs grain count, batch count, auxiliary metric count, duration, execution mode, and fallback reason.

Evidence:

- `mvn test -pl foggy-dataset-model -am "-Dtest=NonAdditiveRollupExecutorDomainSliceTest,PivotAxisDomainSqlPlannerTest,PivotTopNSqlPlannerTest,MetricAdditivityAnalyzerTest" "-P!multi-db" "-Dsurefire.failIfNoSpecifiedTests=false"` passed with 43 tests.
- SQLite/MySQL8/PostgreSQL parity logs include pushdown success markers and non-additive auxiliary query start/completion markers.

Remaining:

- Promote these log markers into counters/timers if the project standardizes a metrics registry.
- Add production dashboard or log-query examples after deployment telemetry is available.

### Stage 5A Large-Domain Transport (PIVOT-91-B1)

Status: `signed-off-with-risks`.

Decision:

- PIVOT-91-B1 is marked complete/review-ready with the design for `DomainTransportPlan` and `DomainRelationRenderer`.
- MySQL 5.7 is planned to use Derived Table (UNION ALL) fallback, but still fail-closed if parameters exceed safe limits.
- PIVOT-91-B2 (Production Implementation) is still blocked/pending on B1 acceptance.
- `domain > 500` large-domain cases currently still fail closed.
- Release recommendation after this milestone remains `beta`, not release candidate, as Stage 5B advanced semantics are still open.

Acceptance Status:

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/pivot-stage5a-b1-domain-transport-acceptance.md`
- blocking_items: none
- follow_up_required: yes

### Stage 5A Domain Transport (PIVOT-91-B2-Prep)

Status: `prep-signed-off-with-risks`.

Implemented:
- Added internal B2 implementation contract to B1 design document.
- Added `DomainTransportPlan`, `DomainTransportField`, `DomainTransportTuple` under `transport` package.
- Added `DomainRelationRenderer` SPI and specific dialect renderer skeletons (PostgreSQL, SQLite, MySQL 8, MySQL 5.7).
- Preserved `domain > 500` fail-closed behavior without modifying production query paths.
- Added unit coverage for rendering logic (`DomainRelationRendererTest`).

Remaining:
- B2 Production Enablement (Wait for telemetry / product demand before hooking up internal carrier to `JdbcModelQueryEngine`).

Quality / Acceptance:

- quality_status: reviewed
- quality_decision: ready-with-risks
- quality_record: `docs/9.1.0/quality/pivot-stage5a-b2-prep-implementation-quality.md`
- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/pivot-stage5a-b2-prep-acceptance.md`
- blocking_items: none
- follow_up_required: yes

### Cascade Generate Semantic Design (PIVOT-91-C1)

Status: `signed-off-with-risks`.

Implemented:
- Added `docs/9.1.0/detailed_design/11_pivot_stage5b_cascade_generate_semantics.md`.
- Defined semantic boundaries for multi-level TopN, Having order, tie-breaking, and uneven domains.
- Provided SQL Oracle CTE examples for cascading limits and having clauses.
- Highlighted why current beta behavior (evaluating inner TopN globally against `_base_relation` without staged filtering) is unsafe.
- Documented rejection boundaries for non-additive subtotals with multi-level TopN.

Remaining:
- `PIVOT-91-C2` (Implementation) remains gated. No production Java behavior was modified. Release recommendation remains `beta`.

Acceptance Status:

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/pivot-stage5b-c1-cascade-generate-semantics-acceptance.md`
- blocking_items: none
- follow_up_required: yes

### 9.1.0 Phase Closeout

Status: `signed-off-with-risks`.

Decision:

- Current 9.1.0 Pivot Engine phase is accepted with risks.
- Release recommendation remains `beta / not RC`.
- B2 production enablement, C2 implementation, and D/E roadmap tracks remain pending, gated, or deferred.

Acceptance Status:

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/version-phase-signoff.md`
- blocking_items: none
- follow_up_required: yes
