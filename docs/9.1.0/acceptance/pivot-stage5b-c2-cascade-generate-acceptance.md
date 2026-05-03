---
acceptance_scope: feature
version: 9.1.0
target: PIVOT-91-C2 Cascade Generate implementation
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex acceptance reviewer
signed_off_at: 2026-05-02
reviewed_by: Codex quality reviewer
blocking_items: []
follow_up_required: yes
evidence_count: 5
doc_role: feature_acceptance
doc_purpose: Sign off PIVOT-91-C2 after implementation quality gate and coverage audit.
---

# PIVOT-91-C2 Cascade Generate Acceptance

## Background

PIVOT-91-C2 implements the accepted C2 v1 subset for multi-level Cascade Generate / TopN: rows-axis two-level cascade, additive ranking and having metrics, explicit `orderBy`, staged SQL only, deterministic tie-breaking, and explicit refusal for ambiguous or unsupported cases.

The acceptance standard is LLM-safe behavior: when semantics or execution support cannot be proven, the engine must fail closed with a rewriteable error instead of producing a best-effort result.

## Acceptance Basis

- `docs/9.1.0/detailed_design/11_pivot_stage5b_cascade_generate_semantics.md`
- `docs/9.1.0/detailed_design/12_pivot_stage5b_cascade_generate_disambiguation.md`
- `docs/9.1.0/detailed_design/13_pivot_stage5b_c2_implementation_plan.md`
- `docs/9.1.0/quality/pivot-stage5b-c2-cascade-generate-implementation-quality.md`
- `docs/9.1.0/test_coverage/pivot-stage5b-c2-cascade-generate-coverage-audit.md`
- `docs/9.1.0/workitems/pivot-java-engine-9.1.0-followups.md`
- `PivotPipeline.java`
- `PivotAxisDomainSqlPlanner.java`
- `pivot/cascade/*`
- `PivotCascadeGenerateSqlParityIntegrationTest.java`
- `PivotCascadeGenerateValidationTest.java`
- `PivotAxisDomainSqlPlannerCascadeTest.java`
- `PivotAxisDomainSqlPlannerTest.java`

## Acceptance Checklist

| Requirement | Result | Notes |
|---|---|---|
| No public Pivot DSL change | pass | C2 is internal planner behavior plus validation. |
| queryModel lifecycle preserved | pass | SQL generation remains based on managed relation output. |
| Cascade memory fallback forbidden | pass | Cascade planner failure raises `PIVOT_CASCADE_SQL_REQUIRED`. |
| Ranking grain after parent filtering | pass | Child domain CTE joins parent surviving domain before child aggregation/ranking. |
| Having before TopN at same level | pass | Domain filtered CTE precedes ranked CTE. |
| Deterministic tie-breaking | pass | Metric NULL bucket plus dimension NULL buckets and prefix/current key ordering are now enforced. |
| Cross-axis isolation | pass | C2 v1 rejects row cascade plus column domain operations. |
| Additive subtotal/grandTotal over surviving domain | pass | Integration test verifies surviving-domain subtotal behavior. |
| Non-additive and ambiguous semantics fail closed | pass | Non-additive, tree, three-level, cross-axis, having-only, and missing-orderBy cases are refused. |
| Dialect refusal boundary | pass with risk | SQLite/PostgreSQL/MySQL 8 are allowed candidates; SQL Server and conservative MySQL 5.7 are refused. |

## Evidence

- `mvn -pl foggy-dataset-model "-Dtest=PivotAxisDomainSqlPlannerTest,PivotAxisDomainSqlPlannerCascadeTest" test` passed.
- `mvn -pl foggy-dataset-model -Dtest=PivotCascadeGenerateValidationTest test` passed with 9 tests across the default, mysql, and postgres surefire executions.
- `mvn -pl foggy-dataset-model -Dtest=PivotCascadeGenerateSqlParityIntegrationTest test` passed.
- `mvn -pl foggy-dataset-model "-Dtest=PivotIntegrationTest#testParentChildTopN" test` passed.
- `git diff --check` passed with only expected Windows LF-to-CRLF warnings.

## Non-Blocking Risks

- SQL Server remains deferred and explicitly refused until dialect-specific SQL oracle and CI evidence exist.
- MySQL 5.7 live database refusal is not recorded as a dedicated external profile in this gate; current behavior is fail-closed through planner support checks and a pipeline-boundary refusal test.
- C2 v1 intentionally excludes tree mode, cross-axis cascade, three-level cascade, having-only cascade, and non-additive cascade totals.

## Final Decision

`accepted-with-risks`

PIVOT-91-C2 is accepted for the scoped C2 v1 behavior. Unsupported or unproven semantic shapes must remain fail-closed with explicit `PIVOT_CASCADE_*` errors.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/pivot-stage5b-c2-cascade-generate-acceptance.md`
- quality_record: `docs/9.1.0/quality/pivot-stage5b-c2-cascade-generate-implementation-quality.md`
- coverage_record: `docs/9.1.0/test_coverage/pivot-stage5b-c2-cascade-generate-coverage-audit.md`
- blocking_items: none
- follow_up_required: yes
