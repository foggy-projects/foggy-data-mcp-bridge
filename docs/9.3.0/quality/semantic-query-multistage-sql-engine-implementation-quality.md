---
quality_scope: feature
quality_mode: pre-coverage-audit
version: 9.3.0
target: semantic-query-multistage-sql-engine
status: reviewed
decision: ready-with-risks
reviewed_by: Codex
reviewed_at: 2026-07-09
follow_up_required: yes
---

# Implementation Quality Gate

## Background

This quality gate reviews the Stage 5 implementation of the 9.3.0 JDBC semantic query multi-stage SQL engine and the P0-P2 follow-up preAgg restoration. The reviewed scope includes planner-owned stage detection, renderer dispatch cleanup, fail-closed planner diagnostics, Odoo dialect shape tests, release-level regression evidence, bounded stage-aware `returnTotal` preAgg equivalence, and strict fail-closed preAgg mapping proof.

## Check Basis

- `docs/9.3.0/workitems/P0-semantic-query-multistage-sql-engine.md`
- `docs/9.3.0/detailed_design/00_semantic_query_multistage_sql_engine.md`
- `docs/9.3.0/workitems/P0-semantic-query-multistage-sql-engine-progress.md`
- Current implementation under `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine`
- Current regression tests under `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model`

## Changed Surface

- `QueryStagePlanner` now owns window, post-aggregate, and postSlice feature detection for covered paths.
- `QueryStagePlan` exposes renderer and unsupported-reason helper methods.
- `JdbcModelQueryEngine` dispatches post-aggregate/window/single-pass rendering from the stage plan instead of local duplicated flags.
- Stage diagnostics now record `post-slice-result-stage-required` and `post-aggregate-window-mix-unsupported` before fail-closed exceptions.
- Odoo post-aggregate tests allow either CTE or derived-table stage shape based on dialect capability.
- `QueryStagePlan` now distinguishes `return-total-equivalent-only` from unsafe final-stage preAgg rewrites.
- `PreAggRewriteStep` keeps main-query preAgg disabled for final-stage-required plans, but can attach preAgg aggregate SQL for no-result-filter equivalent `returnTotal` plans.
- `FinalStagePreAggAggregateSqlBuilder` builds equivalent final-stage aggregate SQL from mapped preAgg group fields and measures, and skips aggregate preAgg when equivalence cannot be proven.
- The equivalent builder validates actual JDBC group fields before request `groupBy` fallback, and fails closed for hybrid matches, undeclared preAgg dimension properties, undeclared measures, unmappable groups, partially mappable groupBy lists, and unprovable `$field`/`$expr`/logical slice predicates.

## Quality Checklist

| Check | Status | Notes |
|---|---|---|
| Scope matches approved work item | pass | Stage 5 cleanup stays within the JDBC semantic query engine and docs. |
| Non-goals preserved | pass | No Step loop planner, public API change, UI change, or model syntax change was introduced. |
| Architecture direction preserved | pass | Stage ownership moved toward `QueryStagePlanner`; engine orchestration is thinner. |
| Failure behavior explicit | pass | Unsupported mixed/result-stage cases fail closed with planner diagnostics. |
| Backwards compatibility for single-stage paths | pass | Full module regression passed. |
| Dialect capability respected | pass_with_risk | MySQL no-CTE rendering is covered by fixtures/profile tests; docker profile execution passed; true MySQL 5.7 server evidence is pending; SQL Server profile is connectivity-blocked. |
| PreAgg correctness and bounded restoration | pass | Unsafe final-stage result-filter plans still skip preAgg; no-result-filter returnTotal equivalent plans can use preAgg aggregate SQL; hybrid/unprovable mapping and predicate-proof cases fail closed. |
| Test evidence attached | pass | Targeted, compose, Odoo, and full module tests are recorded in the progress file. |
| Documentation updated | pass | Progress, quality, coverage, and acceptance records are written under `docs/9.3.0`. |

## Findings

No blocking implementation quality issues were found in the Stage 5 or P0-P2 follow-up diff.

The 2026-07-09 review finding on final-stage equivalent preAgg predicate proof has been closed by a strict final-stage-only proof path. Unprovable predicates now skip aggregate preAgg with `return-total-equivalent-predicate-not-provable`.

The main remaining concern is evidence, not code structure: SQL Server profile execution was attempted but blocked by JDBC pre-login connection reset on `localhost:11433`, and true MySQL 5.7 server execution is still pending. The P1 preAgg restoration is intentionally narrow and should not be treated as broad final-stage preAgg optimization.

## Risks / Follow-ups

- Fix or reprovision the SQL Server focused profile datasource, then rerun the profile.
- Run true MySQL 5.7 execution for post-aggregate derived fallback and `returnTotal` final-stage semantics.
- Broaden stage-aware preAgg equivalence only with additional proof and tests for final-stage filters, mixed stages, non-trivial group/measure mappings, and any row-level measure predicate semantics.

## Recommended Next Skills

- `foggy-test-coverage-audit`
- `foggy-acceptance-signoff`

## Decision

Decision: ready-with-risks.

The implementation is coherent and maintainable enough to proceed to coverage audit and acceptance with documented dialect evidence gaps.
