---
doc_role: release_owner_review
doc_purpose: Summarize the included PIVOT-91-C2 Cascade Generate implementation state for release-owner review.
version: 9.1.0
target: PIVOT-91-C2 Cascade Generate release inclusion
status: included-in-rc2
created_at: 2026-05-02
decision_required: no
---

# PIVOT-91-C2 Release Owner Review

## Document Purpose

- doc_type: release-owner-review
- intended_for: release-owner | reviewer | signoff-owner
- purpose: Provide one entry point for reviewing the accepted C2 v1 Cascade Generate implementation included in `v9.1.0-rc.2`.

## Current Position

PIVOT-91-C2 is implemented and feature-signed-off as `accepted-with-risks` for the C2 v1 subset:

- rows-axis two-level cascade Generate / TopN;
- additive ranking and having metrics only;
- explicit `orderBy` on limited cascade levels;
- staged SQL execution only;
- deterministic metric and dimension-key NULL bucket tie-breaking;
- no current memory fallback for cascade requests;
- explicit `PIVOT_CASCADE_*` refusal for ambiguous or unsupported shapes.

The earlier tag `v9.1.0-rc.1` points to commit `0c146da7` and remains the B2-oriented RC. The C2-inclusive closeout is represented by `v9.1.0-rc.2`.

## Release Owner Decision

| Decision | Meaning | Recommendation |
|---|---|---|
| C2 included in `v9.1.0-rc.2` | The scoped rows two-level cascade implementation is part of the current RC candidate. | Accepted with risks; keep all unsupported shapes fail-closed. |
| Remaining unresolved items deferred | SQL Server cascade, MySQL 5.7 live evidence, tree/cross-axis/deeper cascade, and outer Pivot cache are not 9.1.0 blockers. | Track in 9.2.0 follow-up docs. |

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

## Commit / Tag Review Notes

The C2 implementation is represented by:

- `ea8b7e48 feat(pivot): implement c2 cascade generate topn`
- `60d9be6e test(pivot): cover preagg topn parameter order`
- `v9.1.0-rc.2` at the C2-inclusive closeout point

## Progress Tracking

| Dimension | Status | Notes |
|---|---|---|
| Development | complete | C2 v1 implementation and post-signoff hardening are complete. |
| Testing | pass | Targeted validation, staged-shape, parity, regression, and diff hygiene checks passed. |
| Experience | N/A | Pure backend SQL planner / validation behavior; no UI surface changed. |

## Release Recommendation

Release notes must describe C2 as scoped rows two-level cascade support, not general MDX compatibility or arbitrary multi-level TopN support. The unsupported cases listed above must remain explicit refusals or 9.2.0 follow-ups.
