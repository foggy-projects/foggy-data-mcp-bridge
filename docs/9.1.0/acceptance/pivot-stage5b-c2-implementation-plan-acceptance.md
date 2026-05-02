---
doc_role: feature_acceptance
doc_purpose: Sign off the PIVOT-91-C2 Cascade Generate implementation plan for 9.1.0 without authorizing implementation completion.
acceptance_scope: feature
version: 9.1.0
target: PIVOT-91-C2 Cascade Generate implementation plan
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex acceptance reviewer
signed_off_at: 2026-05-02
reviewed_by: Codex
blocking_items: []
follow_up_required: yes
evidence_count: 8
---

# Feature Acceptance

## Background

PIVOT-91-C2 is the future implementation track for Stage 5B Cascade Generate and multi-level TopN. C1 and C1.1 are already signed off with risks as semantic and LLM-safe design gates. This acceptance reviews only the C2 implementation planning document, not Java implementation, tests, or runtime behavior.

The purpose of the C2 plan is to convert the signed-off C1.1 boundaries into a test-first implementation sequence with concrete test classes, phase exit criteria, and hard fail-closed rules. PIVOT-91-C2 implementation remains gated after this acceptance.

## Acceptance Basis

- `docs/9.1.0/README.md`
- `docs/9.1.0/detailed_design/00_java_pivot_engine_roadmap.md`
- `docs/9.1.0/detailed_design/11_pivot_stage5b_cascade_generate_semantics.md`
- `docs/9.1.0/detailed_design/12_pivot_stage5b_cascade_generate_disambiguation.md`
- `docs/9.1.0/detailed_design/13_pivot_stage5b_c2_implementation_plan.md`
- `docs/9.1.0/acceptance/pivot-stage5b-c1.1-cascade-generate-disambiguation-acceptance.md`
- `docs/9.1.0/workitems/pivot-java-engine-9.1.0-followups.md`
- Local verification commands listed in this record.

## Checklist

- [x] The plan inherits the C1.1 fail-closed rule and keeps current memory fallback forbidden for cascade requests.
- [x] The plan keeps C2 v1 scoped to rows two-level cascade, additive rank/having metrics, explicit `orderBy`, staged SQL only, and supported candidate dialects.
- [x] The plan preserves rejected cases for columns cascade, cross-axis cascade, three-level cascade, having-only cascade, non-additive ranking/having/totals, tree mode, parentShare/baselineRatio, unsupported dialects, and planner failure.
- [x] The plan maps all 10 oracle/refusal cases to proposed concrete test classes and method names.
- [x] The plan distinguishes real SQL parity cases from refusal/error-code tests.
- [x] The plan defines phase exit criteria for validation, staged SQL planner, additive totals, dialect parity/refusal, and quality/coverage/acceptance.
- [x] The plan explicitly states that cascade planner failure must become `PIVOT_CASCADE_SQL_REQUIRED` and must not enter the current memory execution path.
- [x] The C2 workitem remains `gated` and no C2 acceptance marker incorrectly claims implementation signoff.
- [x] No Java implementation is authorized by this acceptance.

## Failed Items

No blocking failed items were found for the C2 implementation plan gate.

## Evidence

- `13_pivot_stage5b_c2_implementation_plan.md` defines purpose, non-goals, accepted semantic baseline, C2 v1 execution pipeline, code touchpoints, error model, oracle/refusal test matrix, implementation phases, and explicit start gate.
- The oracle/refusal matrix maps all 10 C1.1 cases to proposed test classes and method names, including `PivotCascadeGenerateValidationTest`, `PivotAxisDomainSqlPlannerCascadeTest`, `PivotCascadeGenerateSqlParityIntegrationTest`, and `PivotPipelineCascadeFallbackTest`.
- The matrix marks which cases require real SQL data parity and which are refusal/error-code tests.
- The phase plan includes exit criteria for exact error-code assertions, staged CTE SQL shape tests, surviving-domain additive totals, dialect parity/refusal, and final quality/coverage/acceptance.
- The plan explicitly preserves the no-current-memory-fallback rule for cascade requests.
- `workitems/pivot-java-engine-9.1.0-followups.md` keeps PIVOT-91-C2 status as `gated` and records planning status separately from implementation or acceptance status.
- `README.md` still states C2 remains gated until implementation approval is explicit and the C1.1 SQL oracle/refusal tests are implemented and passing.
- `git status --short --branch` shows this task is documentation-focused, while pre-existing unrelated diffs remain visible for owner review.

## Risks / Open Items

- This acceptance signs off the implementation plan only. It does not prove Java implementation, SQL generation, runtime refusal behavior, or cross-dialect parity.
- The proposed test class and method names are planning commitments; they still need to be created or reconciled with existing test classes during implementation.
- Pre-existing worktree changes remain outside this acceptance scope:
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotSqlParityIntegrationTest.java`
  - `docs/9.0.0.beta/test_coverage/pivot-sql-topn-pushdown-coverage-audit.md`
- PIVOT-91-C2 implementation still requires explicit approval before Java or test changes begin.
- C2 completion must still pass implementation quality gate, test coverage audit, and final feature acceptance after code and tests exist.

## Final Decision

PIVOT-91-C2 implementation planning is signed off as `accepted-with-risks`.

The plan is sufficiently concrete to guide a future test-first implementation prompt. PIVOT-91-C2 implementation remains `gated` until explicit approval is given. This acceptance must not be interpreted as implementation completion or production readiness.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/pivot-stage5b-c2-implementation-plan-acceptance.md`
- blocking_items: none
- follow_up_required: yes
