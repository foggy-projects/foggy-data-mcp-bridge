---
doc_role: release_owner_review
doc_purpose: Summarize the PIVOT-91-C2 Cascade Generate implementation state for release-owner inclusion decisions.
version: 9.1.0
target: PIVOT-91-C2 Cascade Generate release inclusion
status: ready-for-release-owner-review
created_at: 2026-05-02
decision_required: yes
---

# PIVOT-91-C2 Release Owner Review

## Document Purpose

- doc_type: release-owner-review
- intended_for: release-owner | reviewer | signoff-owner
- purpose: Provide one entry point for deciding whether the accepted C2 v1 Cascade Generate implementation should be included in the next 9.1.0 RC.

## Current Position

PIVOT-91-C2 is implemented and feature-signed-off as `accepted-with-risks` for the C2 v1 subset:

- rows-axis two-level cascade Generate / TopN;
- additive ranking and having metrics only;
- explicit `orderBy` on limited cascade levels;
- staged SQL execution only;
- deterministic metric and dimension-key NULL bucket tie-breaking;
- no current memory fallback for cascade requests;
- explicit `PIVOT_CASCADE_*` refusal for ambiguous or unsupported shapes.

The local tag `v9.1.0-rc.1` currently points to commit `0c146da7`, which predates the uncommitted C2 implementation/signoff changes in the working tree. Do not move or reuse `v9.1.0-rc.1` for C2. If C2 is included in a release candidate, commit the C2 changes and create a new RC tag.

## Release Owner Decision

| Option | Meaning | Recommendation |
|---|---|---|
| Keep `v9.1.0-rc.1` scope | Release the existing B2-oriented RC without C2 implementation. | Safe if the release goal is only Stage 5A production transport. |
| Include C2 in next RC | Commit the C2 implementation/signoff changes and cut a new tag such as `v9.1.0-rc.2`. | Recommended only if product wants rows two-level cascade in 9.1.0. |
| Defer C2 after 9.1.0 | Keep C2 signed off locally but do not include it in the 9.1.0 release line. | Safe if release risk appetite is low. |

## Accepted C2 v1 Scope

| Area | Accepted Behavior |
|---|---|
| Axis scope | Rows axis only. |
| Level count | Exactly two row levels for cascade requests. |
| Ranking grain | Current-level aggregate after parent surviving-domain filtering. |
| Having order | Same-level having runs before TopN. |
| Tie-breaking | Metric NULL bucket, metric direction, dimension key NULL bucket, prefix/current key order. |
| Totals | Additive subtotal/grandTotal over surviving domain. |
| Supported dialect candidates | SQLite, PostgreSQL, MySQL 8 dialect profile. |
| Error behavior | Unsupported shapes fail closed with rewriteable `PIVOT_CASCADE_*` errors. |

## Explicitly Refused / Deferred

- SQL Server cascade execution.
- Conservative MySQL / MySQL 5.7 cascade execution.
- Tree mode with cascade TopN.
- Cross-axis cascade or rows cascade plus column domain operations.
- Three-level or deeper cascade.
- Having-only multi-level cascade without accepted oracle coverage.
- Non-additive cascade ranking, having, subtotal, or grandTotal.
- Current memory fallback for cascade requests.

## Evidence Links

- Quality gate: `docs/9.1.0/quality/pivot-stage5b-c2-cascade-generate-implementation-quality.md`
- Coverage audit: `docs/9.1.0/test_coverage/pivot-stage5b-c2-cascade-generate-coverage-audit.md`
- Acceptance signoff: `docs/9.1.0/acceptance/pivot-stage5b-c2-cascade-generate-acceptance.md`
- Workitem tracker: `docs/9.1.0/workitems/pivot-java-engine-9.1.0-followups.md`
- Implementation plan: `docs/9.1.0/detailed_design/13_pivot_stage5b_c2_implementation_plan.md`
- Disambiguation contract: `docs/9.1.0/detailed_design/12_pivot_stage5b_cascade_generate_disambiguation.md`

## Verification Snapshot

| Gate | Result |
|---|---|
| C2 validation/refusal | `mvn -pl foggy-dataset-model -Dtest=PivotCascadeGenerateValidationTest test` passed with 9 tests across default, mysql, and postgres surefire executions. |
| C2 staged SQL shape | `mvn -pl foggy-dataset-model "-Dtest=PivotCascadeGenerateValidationTest,PivotAxisDomainSqlPlannerCascadeTest" test` passed. |
| C2 SQL parity | `mvn -pl foggy-dataset-model -Dtest=PivotCascadeGenerateSqlParityIntegrationTest test` passed with 6 tests. |
| Existing parent-child TopN regression | `mvn -pl foggy-dataset-model "-Dtest=PivotIntegrationTest#testParentChildTopN" test` passed. |
| Diff hygiene | `git diff --check` passed with only expected Windows LF-to-CRLF warnings. |

## Worktree Review Notes

The current working tree contains both C2 changes and earlier test-supplement/documentation changes that should be reviewed together before a release commit:

- C2 production code: `PivotPipeline.java`, `PivotAxisDomainSqlPlanner.java`, `pivot/cascade/*`.
- C2 tests: `PivotCascadeGenerateSqlParityIntegrationTest.java`, `PivotCascadeGenerateValidationTest.java`, `PivotAxisDomainSqlPlannerCascadeTest.java`, `PivotAxisDomainSqlPlannerTest.java`.
- Prior test supplement files: `PivotSqlParityIntegrationTest.java`, `docs/9.0.0.beta/test_coverage/pivot-sql-topn-pushdown-coverage-audit.md`.
- C2 docs and signoff records under `docs/9.1.0`.

## Progress Tracking

| Dimension | Status | Notes |
|---|---|---|
| Development | complete | C2 v1 implementation and post-signoff hardening are complete. |
| Testing | pass | Targeted validation, staged-shape, parity, regression, and diff hygiene checks passed. |
| Experience | N/A | Pure backend SQL planner / validation behavior; no UI surface changed. |

## Release Recommendation

If C2 is included, create a new release candidate after committing the current C2 implementation and documentation changes. The release notes must describe C2 as scoped rows two-level cascade support, not general MDX compatibility or arbitrary multi-level TopN support.
