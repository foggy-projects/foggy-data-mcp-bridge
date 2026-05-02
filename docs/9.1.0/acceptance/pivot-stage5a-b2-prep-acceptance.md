---
doc_role: feature_acceptance
doc_purpose: Sign off the PIVOT-91-B2-Prep Stage 5A domain transport skeleton for 9.1.0.
acceptance_scope: feature
version: 9.1.0
target: PIVOT-91-B2-Prep Stage 5A domain transport skeleton
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex acceptance reviewer
signed_off_at: 2026-05-02
reviewed_by: Codex
blocking_items: []
follow_up_required: yes
evidence_count: 7
---

# Feature Acceptance

## Background

PIVOT-91-B2-Prep follows the accepted-with-risks B1 design. Its target is to freeze the implementation contract and add internal domain transport renderer skeletons without enabling production large-domain transport.

## Acceptance Basis

- `docs/9.1.0/detailed_design/00_java_pivot_engine_roadmap.md`
- `docs/9.1.0/detailed_design/10_pivot_stage5a_domain_transport_plan.md`
- `docs/9.1.0/acceptance/pivot-stage5a-b1-domain-transport-acceptance.md`
- `docs/9.1.0/quality/pivot-stage5a-b2-prep-implementation-quality.md`
- `docs/9.1.0/workitems/pivot-java-engine-9.1.0-followups.md`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/transport/`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/transport/DomainRelationRendererTest.java`

## Checklist

- [x] B2 implementation contract is documented.
- [x] Internal skeleton compiles.
- [x] Renderer/refusal unit tests pass.
- [x] Current `domain > 500` runtime behavior remains fail-closed.
- [x] No public Pivot DSL/API change was introduced.
- [x] Workitems are updated with prep status and evidence.
- [x] Production Stage 5A remains pending.

## Failed Items

No blocking failed items were found for the B2-Prep scope.

## Evidence

- `git status --short --branch` showed branch `9.1.0` with prior A1/A2/B1 work preserved and new transport skeleton files added.
- `rg` confirmed the new transport package is referenced by its tests and docs only; production pivot execution paths do not consume it yet.
- `DomainTransportPlan` now validates empty plans, blank fields, and tuple arity mismatch before rendering.
- `Mysql8ValuesDomainRenderer` now gates `VALUES ROW(...)` on MySQL `8.0.19+` using leading numeric version segments.
- `Mysql57DerivedTableDomainRenderer` enforces tuple, bind parameter, and SQL length thresholds before rendering.
- `mvn test -pl foggy-dataset-model -am "-Dtest=DomainRelationRendererTest,NonAdditiveRollupExecutorDomainSliceTest,PivotAxisDomainSqlPlannerTest,PivotTopNSqlPlannerTest" "-P!multi-db" "-Dsurefire.failIfNoSpecifiedTests=false"` passed with 30 tests.
- `git diff --check` reported only Windows line-ending warnings and no whitespace errors.

## Risks / Open Items

- This is not production B2. `DomainTransportPlan` is not yet injected into `SemanticRequestContext` or consumed by `JdbcModelQueryEngine`.
- Database product version propagation still needs to be implemented before MySQL8 production rendering can be enabled.
- `DomainTransportCapability` is still a placeholder and should become a real capability/factory contract during production wiring.
- Cross-database SQL data parity for Stage 5A large-domain behavior remains required after runtime integration.
- Release recommendation remains beta because Stage 5A is not production-enabled and Stage 5B semantics are still open.

## Final Decision

PIVOT-91-B2-Prep is signed off as `accepted-with-risks`. The skeleton is acceptable for the prep milestone and preserves the required fail-closed runtime boundary. It does not authorize production enablement of large-domain transport.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/pivot-stage5a-b2-prep-acceptance.md`
- blocking_items: none
- follow_up_required: yes
