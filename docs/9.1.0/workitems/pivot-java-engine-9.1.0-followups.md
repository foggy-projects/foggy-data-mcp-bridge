---
doc_role: workitem_index
doc_purpose: Track executable Java Pivot Engine follow-up items for 9.1.0.
version: 9.1.0
target: Java Pivot Engine Follow-Ups
status: release-candidate signed-off-with-risks
created_at: 2026-05-02
---

# Pivot Java Engine 9.1.0 Follow-Ups

## Workitem Table

| ID | Priority | Item | Status | Owner | Exit Criteria |
|---|---|---|---|---|---|
| PIVOT-91-A1 | P0 | CI release readiness for Pivot Java Core | complete | Java Core / CI | SQLite PR gate and MySQL8/PostgreSQL release gate documented and runnable |
| PIVOT-91-A2 | P0 | Pushdown/fallback/domain-limit telemetry | complete | Java Core | Safe log markers cover pushdown success/fallback/skip, fallback reason, domain-limit exceptions, domain size, and non-additive auxiliary query count/duration |
| PIVOT-91-B1 | P1 | Stage 5A `DomainTransportPlan` design | complete | Java Core | Design accepted with dialect matrix and queryModel lifecycle preservation |
| PIVOT-91-B2 | P1 | Stage 5A production implementation | complete | Java Core | Large-domain transport is wired into non-additive auxiliary rollup and verified on SQLite/MySQL8/PostgreSQL |
| PIVOT-91-C1 | P1 | Cascade Generate semantic design | complete | Java Core / Product | Multi-level TopN semantics frozen with SQL oracle examples |
| PIVOT-91-C1.1 | P1 | Cascade Generate LLM-safe disambiguation | signed-off-with-risks | Java Core / Product | C2 whitelist, explicit fail-closed cases, rewriteable errors, and SQL oracle requirements accepted |
| PIVOT-91-C2 | P2 | Cascade Generate implementation | signed-off-with-risks | Java Core | C2 v1 rows two-level cascade implemented, oracle/refusal tests passing, and acceptance signed off with fail-closed residual risks |
| PIVOT-91-D1 | P2 | Tree SQL TopN / tree subtotal feasibility | proposed | Java Core | Feasibility report with closure-table/descendant strategy decision |
| PIVOT-91-E1 | P2 | Outer Pivot cache feasibility | proposed | Java Core / Cache | Cache key and invalidation model accepted |

## Recommended Execution Order

1. PIVOT-91-A1: CI release readiness.
2. PIVOT-91-A2: telemetry.
3. PIVOT-91-B1: Stage 5A design.
4. PIVOT-91-B2 direct production enablement.
5. PIVOT-91-C1 before any advanced Generate implementation.
6. PIVOT-91-C1.1 before any C2 implementation work.
7. PIVOT-91-D1 and PIVOT-91-E1 after ranking semantics and telemetry are clearer.

## Guardrails

- No queryModel bypass.
- No public DSL changes without a separate requirement.
- No default-on Stage 5A implementation without cross-database parity.
- No Stage 5B implementation before C1.1 disambiguation is accepted.
- Cascade requests must fail closed with rewriteable errors when semantics or execution capability are not proven.
- Current memory fallback must not execute cascade requests unless a future staged memory implementation has parity oracle coverage.
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

### Stage 5A Domain Transport Production Enablement (PIVOT-91-B2)

Status: `signed-off-with-risks`.

Decision:

- The product-demand gate was explicitly overridden for this milestone; B2 was completed as direct engine delivery.
- `domain > 500` no longer fails solely because the surviving domain is larger than 500 when the current dialect has a safe transport renderer.
- Unsupported or unsafe cases still fail closed through `DomainTransportRefusalException`.
- No public Pivot DSL changes were introduced.

Implemented:

- `NonAdditiveRollupExecutor` now converts large surviving row/column domains into internal `DomainTransportPlan` instances instead of building oversized `OR-of-AND` slices.
- `SemanticRequestContext` carries the plans internally; `SemanticQueryServiceV3Impl` forwards them through `ModelResultContext.extData`.
- `JdbcModelQueryEngine` renders the transport relation before SQL execution and injects it as a base-relation `WHERE EXISTS` filter, preserving permission checks, systemSlice, preAgg rewrite, parameterization, SQL logging, and sanitizer alignment.
- PostgreSQL and SQLite use CTE `VALUES`; MySQL 8 uses the CTE `VALUES ROW(...)` path only when JDBC metadata reports version `8.0.19+`; conservative MySQL/MySQL 5.7 uses threshold-limited derived-table `UNION ALL`.
- Telemetry logs plan/apply/refusal markers with relation name, dialect, placement, field count, tuple count, and parameter count, without SQL values.

Evidence:

- `./scripts/verify-pivot-v9-release.ps1` passed after B2 production enablement on 2026-05-02.
- `git diff --check` passed after B2 production enablement on 2026-05-02, with only expected Windows LF-to-CRLF warnings.
- `mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest,PivotIntegrationTest,MetricAdditivityAnalyzerTest" "-Dspring.profiles.active=sqlite" "-P!multi-db" "-Dsurefire.failIfNoSpecifiedTests=false"` passed with 66 tests.
- `mvn test -pl foggy-dataset-model "-Dtest=NonAdditiveRollupExecutorDomainSliceTest,PivotAxisDomainSqlPlannerTest,PivotTopNSqlPlannerTest,DomainRelationRendererTest" "-P!multi-db" "-Dsurefire.failIfNoSpecifiedTests=false"` passed with 31 tests.
- `mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest#testLargeDomainTransportQueryModelParity" "-Dspring.profiles.active=mysql8" "-P!multi-db" "-Dsurefire.failIfNoSpecifiedTests=false"` passed.
- `mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest#testLargeDomainTransportQueryModelParity" "-Dspring.profiles.active=postgres" "-P!multi-db" "-Dsurefire.failIfNoSpecifiedTests=false"` passed.

Remaining:

- MySQL 5.7 derived-table transport is intentionally threshold-limited; excessive tuple count, parameter count, or SQL length must remain fail-closed.
- Stage 5B C2 implementation is still gated and should not be coupled to B2.

Acceptance Status:

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/pivot-stage5a-b2-production-acceptance.md`
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

Historical gate:
- At C1 signoff time, `PIVOT-91-C2` was still gated and no production Java behavior had been modified. Current C2 status is tracked in the dedicated C2 section below.

Acceptance Status:

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/pivot-stage5b-c1-cascade-generate-semantics-acceptance.md`
- blocking_items: none
- follow_up_required: yes

### Cascade Generate LLM-Safe Disambiguation (PIVOT-91-C1.1)

Status: `signed-off-with-risks`.

Implemented:
- Added `docs/9.1.0/detailed_design/12_pivot_stage5b_cascade_generate_disambiguation.md`.
- Defined the LLM-safe first principle: cascade requests execute only when semantics are unique and execution is proven deterministic.
- Defined the C2 v1 whitelist: rows two-level cascade, additive rank/having metrics, explicit `orderBy`, staged SQL only, and supported SQL dialects.
- Defined explicit fail-closed cases, including missing `orderBy`, unsupported SQL capability, non-additive ranking/having/totals, tree mode, cross-axis cascade, and current memory fallback.
- Defined rewriteable error categories such as `PIVOT_CASCADE_ORDER_BY_REQUIRED`, `PIVOT_CASCADE_SQL_REQUIRED`, and `PIVOT_CASCADE_NON_ADDITIVE_REJECTED`.
- Listed the required SQL oracle cases that must be mapped to implementation tests before C2 may start.

Required additions completed (2026-05-02):
- Added §13 C1.1 supersession of C1 memory fallback wording for cascade requests.
- Added §14 dialect v1 decision matrix: SQLite/MySQL 8/PostgreSQL as supported candidates, MySQL 5.7 as explicit refusal, SQL Server as v1 rejected/deferred.
- Added §15 having-only cascade decision: multi-level having without limit is rejected in C2 v1 unless decomposable to independent single-level evaluations.
- Added §16 oracle-to-test matrix mapping all 10 oracle cases to test type, dialect profile, expected behavior, and error codes for refusal cases.
- Reconciled C1 doc 11 §12 memory fallback statement to clarify single-level vs cascade applicability.

Historical gate:
- At C1.1 signoff time, `PIVOT-91-C2` remained gated until implementation approval was explicit and the documented SQL oracle/refusal tests were implemented and passing. Current C2 status is tracked in the dedicated C2 section below.

Development / Testing / Experience Tracking:
- development: documentation-only record updated; no production code changed.
- testing: not run; no executable code changed.
- experience: N/A, no UI or user interaction surface changed.

### Cascade Generate Implementation (PIVOT-91-C2)

Status: `signed-off-with-risks`.

Implemented:
- Created `docs/9.1.0/detailed_design/13_pivot_stage5b_c2_implementation_plan.md` detailing the C2 v1 execution pipeline, code touchpoints, error model, and oracle test matrix.
- Added Phase 1 structured C2 refusal model.
- Added fail-closed validation for missing cascade `orderBy`, tree mode, three-level scope, cross-axis interaction, having-only cascade, and non-additive cascade metrics.
- Updated `PivotPipeline` so cascade requests cannot fall back to the current memory execution path.
- Added Phase 2 staged SQL generation for rows two-level cascade in `PivotAxisDomainSqlPlanner`.
- Generated multi-level CTEs (`_row_domain_1`, `_row_ranked_1`, `_row_filtered_1`, `_row_domain_2`, etc.) with surviving domain joins.
- Implemented deterministic tie-breaking using explicit NULL buckets and prefix keys.
- Handled SQL compilation compilation errors for existing 1-level TopN scenarios in `PivotAxisDomainSqlPlannerTest` by updating assertion checks to accommodate table aliases `b.`.
- Phase 3 Additive Totals: Confirmed additive row subtotal and grandTotal compute successfully over surviving domain in integration tests.
- Phase 4 Dialect Parity/Refusal: Added and stabilized SQL parity oracle tests (`PivotCascadeGenerateSqlParityIntegrationTest`) covering parent ranking, child ranking inside surviving parent domain, parent having before child rank, child having isolation, deterministic null tie-breaking, and additive subtotal surviving-domain behavior.
- Phase 5 Quality Gate: Corrected deterministic dimension-key NULL bucket tie-breaking and restricted C2 planner support to the accepted SQLite/PostgreSQL/MySQL 8 dialect whitelist.
- Phase 5 Acceptance: Added quality, coverage, and acceptance records. C2 v1 is accepted with risks; unsupported semantic shapes and unproven dialects remain fail-closed.

Remaining:
- SQL Server remains rejected/deferred until an accepted SQL oracle and CI profile exist.
- A dedicated live MySQL 5.7 database profile can be added later if MySQL 5.7 remains in release support scope; current behavior is fail-closed through planner refusal and a pipeline-boundary refusal test.
- Tree mode, cross-axis cascade, three-level cascade, having-only cascade, and non-additive cascade totals remain outside C2 v1.

Verification:
- `mvn -pl foggy-dataset-model -Dtest=PivotCascadeGenerateValidationTest test` passed on 2026-05-02 with 9 tests across the default, mysql, and postgres surefire executions.
- `mvn -pl foggy-dataset-model -Dtest=PivotAxisDomainSqlPlannerCascadeTest test` passed on 2026-05-02.
- `mvn -pl foggy-dataset-model -Dtest=PivotAxisDomainSqlPlannerTest test` passed on 2026-05-02.
- `mvn -pl foggy-dataset-model -Dtest=PivotIntegrationTest#testParentChildTopN test` passed on 2026-05-02.
- `mvn -pl foggy-dataset-model -Dtest=PivotCascadeGenerateSqlParityIntegrationTest test` passed on 2026-05-02.
- `git diff --check` passed on 2026-05-02 with only expected Windows LF-to-CRLF warnings.

Acceptance Status:

- planning_status: signed-off-with-risks
- implementation_status: phase5-completed
- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/pivot-stage5b-c2-cascade-generate-acceptance.md`
- quality_record: `docs/9.1.0/quality/pivot-stage5b-c2-cascade-generate-implementation-quality.md`
- coverage_record: `docs/9.1.0/test_coverage/pivot-stage5b-c2-cascade-generate-coverage-audit.md`
- plan_acceptance_record: `docs/9.1.0/acceptance/pivot-stage5b-c2-implementation-plan-acceptance.md`
- blocking_items: none
- follow_up_required: yes

### 9.1.0 Phase Closeout

Status: `signed-off-with-risks`.

Decision:

- Current 9.1.0 Pivot Engine phase is accepted with risks.
- Release recommendation is `release candidate` after the final release script and diff hygiene pass for this B2 production delta.
- C2 implementation is signed off with risks for the C2 v1 rows two-level cascade subset. D/E roadmap tracks remain gated or deferred.

Acceptance Status:

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/version-phase-signoff.md`
- blocking_items: none
- follow_up_required: yes

### 9.1.0 RC Handoff Readiness

Status: `ready-for-release-owner-review`.

Completed:

- Added `docs/9.1.0/acceptance/rc-release-checklist.md` with branch, commit, tag, verification, push, and rollback guidance.
- Added `docs/9.1.0/acceptance/pivot-stage5b-c2-release-owner-review.md` to separate the existing `v9.1.0-rc.1` scope from a possible C2-inclusive next RC.
- Added `docs/9.1.0/operations/stage5a-domain-transport-observability.md` with telemetry marker meanings, operator checks, dialect behavior, and MySQL 5.7 guardrail notes.
- Added `docs/9.1.0/quality/pivot-stage5a-b2-production-quality.md` as a formal implementation quality review for B2 production enablement.
- Re-ran `DomainRelationRendererTest` to confirm renderer-level MySQL 5.7 threshold refusal evidence remains green.

Evidence:

- `mvn test -pl foggy-dataset-model -am "-Dtest=DomainRelationRendererTest" "-P!multi-db" "-Dsurefire.failIfNoSpecifiedTests=false"` passed with 7 tests on 2026-05-02.

Remaining:

- Push `v9.1.0-rc.1` only if release ownership keeps the original B2-oriented RC scope; cut a new RC tag if C2 is included.
- External MySQL 5.7 live-database large-domain parity was not recorded; current MySQL 5.7 evidence is renderer-level threshold/refusal coverage plus fail-closed behavior.
- C2 implementation is separately signed off with risks after the original `v9.1.0-rc.1` tag point; release ownership should cut a new RC tag if C2 is included.
