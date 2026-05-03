---
quality_scope: feature
quality_mode: pre-coverage-audit
version: 9.1.0
target: PIVOT-91-C2 Cascade Generate implementation
status: reviewed
decision: ready-with-risks
reviewed_by: Codex quality reviewer
reviewed_at: 2026-05-02
follow_up_required: yes
---

# PIVOT-91-C2 Cascade Generate Implementation Quality Gate

## Background

PIVOT-91-C2 implements the C2 v1 subset accepted by C1/C1.1: rows-axis two-level cascade Generate, additive ranking and having metrics, explicit `orderBy`, staged SQL execution only, and fail-closed refusal for ambiguous or unproven cases.

This review was run after Phase 4 reported GREEN and before feature acceptance signoff.

## Check Basis

- `CLAUDE.md`
- `docs/9.1.0/detailed_design/11_pivot_stage5b_cascade_generate_semantics.md`
- `docs/9.1.0/detailed_design/12_pivot_stage5b_cascade_generate_disambiguation.md`
- `docs/9.1.0/detailed_design/13_pivot_stage5b_c2_implementation_plan.md`
- `docs/9.1.0/workitems/pivot-java-engine-9.1.0-followups.md`
- Antigravity Phase 4 walkthrough
- `PivotPipeline.java`
- `PivotAxisDomainSqlPlanner.java`
- `pivot/cascade/*`
- `PivotCascadeGenerateSqlParityIntegrationTest.java`
- `PivotCascadeGenerateValidationTest.java`
- `PivotAxisDomainSqlPlannerCascadeTest.java`
- `PivotAxisDomainSqlPlannerTest.java`

## Changed Surface

- `PivotPipeline` now validates cascade request shape and prevents current memory fallback for cascade requests.
- `PivotCascadeRules` defines the C2 v1 whitelist and explicit `PIVOT_CASCADE_*` refusal categories.
- `PivotAxisDomainSqlPlanner` emits staged row-axis domain, having, rank, and filtered-domain CTEs.
- SQL parity and validation tests cover the accepted oracle cases and refusal boundaries.
- Existing test supplement changes in `PivotSqlParityIntegrationTest.java` and `docs/9.0.0.beta/test_coverage/pivot-sql-topn-pushdown-coverage-audit.md` remain outside this signoff scope and were not reverted.

## Quality Checklist

| Area | Result | Notes |
|---|---|---|
| Scope conformance | pass | No public Pivot DSL change. C2 v1 remains rows two-level only. |
| QueryModel lifecycle | pass | Cascade SQL still starts from the managed relation; no queryModel bypass was introduced. |
| Fail-closed behavior | pass | Unsupported cascade requests throw explicit `PIVOT_CASCADE_*` errors instead of producing best-effort results. |
| Memory fallback guard | pass | Planner refusal for cascade requests is converted to `PIVOT_CASCADE_SQL_REQUIRED`; current memory execution is not used. |
| Determinism | pass after gate fix | Rank ordering now includes metric NULL bucket plus dimension-key NULL buckets and full prefix/current key tie-breakers. |
| Dialect boundary | pass after gate fix | C2 planner support is now restricted to SQLite, PostgreSQL, and MySQL 8 dialect profiles; conservative MySQL 5.7 and SQL Server are refused. |
| Complexity | acceptable | Staged CTE generation is more complex than single-level TopN, but the scope is bounded and tests inspect the generated shape. |
| Test hygiene | pass | C2-specific temporary stdout debug output was removed from the parity and staged-shape tests. |

## Findings

No blocking implementation issue remains after the quality gate fixes.

The gate did find and correct two semantic issues before signoff:

- Dimension key tie-breakers originally did not include explicit NULL buckets. That could make equal metric ranks nondeterministic across dialects. The planner and SQL oracles now order by key NULL bucket and key value for every prefix/current key.
- Dialect support originally depended only on CTE/window capability. C2 v1 now also enforces the accepted dialect whitelist so SQL Server and conservative MySQL 5.7 fail closed until explicit oracle coverage exists.

## Residual Risks

- SQL Server remains rejected/deferred because there is no accepted SQL Server oracle or CI profile for cascade ranking semantics.
- MySQL 8 support depends on the runtime selecting a MySQL 8 dialect profile. If a MySQL 8 datasource is incorrectly mapped to the conservative MySQL dialect, C2 cascade fails closed instead of falling back.
- There is no dedicated live MySQL 5.7 database profile in this gate. Planner whitelist and pipeline-boundary refusal are unit-covered; a live profile can be added later if MySQL 5.7 remains in release support scope.
- C2 v1 intentionally excludes tree mode, cross-axis cascade, three-level cascade, having-only cascade, and non-additive cascade totals.

## Decision

`ready-with-risks`

The implementation may proceed to coverage audit and acceptance signoff. Remaining risks are fail-closed or explicitly out of C2 v1 scope.
