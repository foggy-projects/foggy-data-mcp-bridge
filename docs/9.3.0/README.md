---
doc_role: version_followup_plan
doc_purpose: Track 9.3.0 semantic query engine architecture hardening and multi-stage SQL planning work.
version: 9.3.0
status: completed_with_risks
created_at: 2026-07-09
updated_at: 2026-07-09
---

# 9.3.0 Semantic Engine Architecture Hardening

## Document Purpose

- doc_type: version-summary
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track the 9.3.0 long-term architecture work for the semantic query engine.

## Version Goal

9.3.0 focuses on making the JDBC semantic query engine stable enough to serve as a shared foundation for downstream company projects. The main architecture item is a deterministic multi-stage SQL planner for aggregate, post-aggregate, window, result-stage filter, ordering, pagination, and `returnTotal` behavior.

The immediate GitHub issue #120 fix remains valid as a regression fix, but it is not the final architecture. 9.3.0 should move the engine away from implicit alias and aggregation metadata side effects, toward explicit query stages and stage-local symbol tables.

## Work Items

| Item | Doc | Status | Owner Module | Summary |
|---|---|---|---|---|
| P0 Semantic Query Multi-Stage SQL Engine | `workitems/P0-semantic-query-multistage-sql-engine.md` | completed_with_risks | `foggy-dataset-model` | Introduce explicit SQL stage planning for row, aggregate, post-aggregate, window/result, and final SQL phases. |

## Detailed Designs

| Design | Doc | Summary |
|---|---|---|
| Multi-stage SQL architecture | `detailed_design/00_semantic_query_multistage_sql_engine.md` | Defines stage boundaries, symbol resolution, renderer responsibilities, rollout, and test gates. Implemented for covered 9.3.0 JDBC paths with documented dialect evidence gaps. |

## Guardrails

- SQL Server support remains in scope, but issue #120 is not caused by SQL Server missing `SUM(a) / SUM(b)` support.
- Do not solve stage planning by adding a lifecycle Step loop. A bounded execution loop can remain for execution-time interceptors, but SQL planning must be deterministic and explicit.
- Existing query behavior must remain compatible for single-stage requests.
- MySQL 5.7 compatibility is a hard acceptance item. Any multi-stage renderer must check dialect capabilities and use derived-table fallback when CTE is unavailable, or fail closed when a required SQL feature has no supported fallback.
- `returnTotal` must count the semantically final filtered row set before ordering and pagination. Pre-aggregation can replace that count only when it proves the same stage semantics.
- Same-stage select aliases are not visible to expressions in that stage. Safe aggregate-alias expressions may be auto-split to a downstream stage; all other same-stage alias references must fail closed.
- Planner diagnostics must be exposed through stable debug metadata so tests and execution interceptors do not rely only on SQL string assertions.
- Every engine-level behavior change must include real SQL execution tests, not only generated SQL string assertions.
- Multi-dialect coverage must include SQLite plus MySQL/PostgreSQL where available; SQL Server coverage is required for identifier, CTE, aggregate, and window behavior when the SQL Server test profile is available.

## Progress Summary

- development: completed with documented risks. Stage 1 through Stage 5 are implemented for the covered JDBC semantic query paths.
- testing: passed. `mvn -pl foggy-dataset-model test` completed with 3259 tests, 0 failures, 0 errors, and 3 skipped.
- experience: N/A, backend query engine architecture only.
- quality: reviewed, ready-with-risks. See `quality/semantic-query-multistage-sql-engine-implementation-quality.md`.
- coverage: reviewed, ready-with-gaps. See `coverage/semantic-query-multistage-sql-engine-coverage-audit.md`.
- acceptance: signed off as accepted-with-risks. See `acceptance/semantic-query-multistage-sql-engine-acceptance.md`.
- remaining risks: SQL Server profile was not executed in this checkpoint; true MySQL 5.7 server execution evidence is still pending.
