---
quality_scope: feature
quality_mode: pre-coverage-audit
version: 9.1.0
target: PIVOT-91-B2 Stage 5A production domain transport
status: reviewed
decision: ready-with-risks
reviewed_by: Codex implementation quality reviewer
reviewed_at: 2026-05-02
follow_up_required: yes
---

# Implementation Quality Gate

## Background

PIVOT-91-B2 production enablement wires Stage 5A large-domain transport into non-additive auxiliary rollup execution. This review checks whether the implementation is scoped, maintainable enough for RC, and documented with the remaining risks.

## Check Basis

- `docs/9.1.0/detailed_design/10_pivot_stage5a_domain_transport_plan.md`
- `docs/9.1.0/workitems/pivot-java-engine-9.1.0-followups.md`
- `docs/9.1.0/acceptance/pivot-stage5a-b2-production-acceptance.md`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/rollup/NonAdditiveRollupExecutor.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/domain/SemanticRequestContext.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticQueryServiceV3Impl.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/transport/`

## Changed Surface

- Large-domain row/column surviving domains become internal `DomainTransportPlan` instances.
- `SemanticRequestContext` carries transport plans without public Pivot DSL expansion.
- `SemanticQueryServiceV3Impl` forwards plans through `ModelResultContext.extData`.
- `JdbcModelQueryEngine` renders dialect-specific transport relations and injects base-relation `WHERE EXISTS` filters.
- Transport telemetry records planned, applied, and refused states without SQL values.
- SQLite, PostgreSQL, MySQL8, and renderer-level MySQL5.7 behavior have targeted verification.

## Quality Checklist

- [x] Scope conformance: changes are limited to Pivot Java Core, transport internals, docs, and tests.
- [x] No public DSL expansion: no public Pivot request syntax was added.
- [x] QueryModel lifecycle: transport is injected inside the existing queryModel SQL path, preserving permission checks, systemSlice, preAgg, params, logging, and sanitizer behavior.
- [x] Fail-closed behavior: unsupported dialects, unsafe relation names, unsupported null-safe predicates, aggregate calculated fields, and renderer threshold breaches throw `DomainTransportRefusalException`.
- [x] Telemetry safety: markers expose counts, dialect, placement, and sanitized reasons, not raw SQL values.
- [x] Test alignment: release gate, cross-dialect large-domain parity, and renderer threshold tests match the changed surface.
- [x] Documentation writeback: design, workitems, acceptance, release notes, operations guide, and release checklist are updated.

## Findings

- No blocking implementation issue was found in this RC review.
- The largest maintainability concentration remains `JdbcModelQueryEngine`, which now owns renderer selection, SQL injection, predicate construction, and database version lookup for transport.
- Renderer selection is safe for RC because unknown or unsafe cases refuse transport, but a future refactor should extract transport SQL injection into a dedicated collaborator if additional dialects or placements are added.

## Risks / Follow-Ups

- MySQL 5.7 live-database parity was not recorded; current evidence is renderer-level threshold coverage plus fail-closed design.
- Very large transported domains still increase SQL text and bind parameter pressure on supported dialects.
- C2 Cascade Generate implementation must not reuse the B2 transport path as proof that multi-level TopN semantics are complete.
- If production telemetry shows frequent transport refusal on expected-safe dialects, review database version detection and renderer thresholds before GA.

## Recommended Next Skills

- `foggy-test-coverage-audit` if the release owner wants a formal evidence-to-acceptance coverage matrix before GA.
- `foggy-acceptance-signoff` if the RC is promoted to a final package publication record.
- `plan-evaluator` for C2 semantic readiness before implementation.

## Decision

The PIVOT-91-B2 production implementation is `ready-with-risks` for the 9.1.0 RC. No quality blocker prevents RC handoff. The remaining issues are operational risk and future maintainability follow-ups, not release-blocking defects for the accepted RC scope.
