---
doc_role: acceptance_record
doc_purpose: Formal acceptance record for PIVOT-91-B2 Stage 5A production domain transport.
acceptance_scope: feature
version: 9.1.0
target: PIVOT-91-B2 Stage 5A Production Domain Transport
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex acceptance reviewer
signed_off_at: 2026-05-02
reviewed_by: Codex acceptance reviewer
blocking_items: []
follow_up_required: yes
---

# PIVOT-91-B2 Acceptance

## Decision

PIVOT-91-B2 is accepted with risks.

The implementation enables large-domain transport for non-additive auxiliary rollup queries when a supported dialect can render the transport safely. It preserves the public Pivot DSL and queryModel lifecycle. Unsupported dialects and unsafe renderer thresholds remain fail-closed.

## Accepted Scope

- `domain <= 500` keeps the existing `OR-of-AND` slice path.
- `domain > 500` creates internal `DomainTransportPlan` instances instead of directly failing on supported dialects.
- The transport plan travels through `SemanticRequestContext` and `ModelResultContext.extData`.
- `JdbcModelQueryEngine` injects the rendered relation as a base-relation `WHERE EXISTS` filter before aggregation.
- SQLite/PostgreSQL use CTE `VALUES`; MySQL8 uses version-gated CTE `VALUES ROW(...)`; conservative MySQL uses threshold-limited derived-table `UNION ALL`.
- Renderer refusal is a hard stop, not an implicit full-scan fallback.

## Evidence

| Evidence | Result | Notes |
|---|---|---|
| SQLite pivot integration gate | PASS | `PivotSqlParityIntegrationTest`, `PivotIntegrationTest`, and `MetricAdditivityAnalyzerTest` passed with 66 tests. |
| Planner / renderer gate | PASS | `NonAdditiveRollupExecutorDomainSliceTest`, `PivotAxisDomainSqlPlannerTest`, `PivotTopNSqlPlannerTest`, and `DomainRelationRendererTest` passed with 31 tests. |
| MySQL8 large-domain parity | PASS | `PivotSqlParityIntegrationTest#testLargeDomainTransportQueryModelParity` passed with `spring.profiles.active=mysql8`. |
| PostgreSQL large-domain parity | PASS | `PivotSqlParityIntegrationTest#testLargeDomainTransportQueryModelParity` passed with `spring.profiles.active=postgres`. |

## Residual Risks

- MySQL 5.7 derived-table transport is intentionally limited by tuple count, bind parameter count, and rendered SQL length.
- Very large supported-domain requests still consume bind parameters and SQL text; operational thresholds should be revisited with production telemetry.
- Stage 5B C2 cascade-generate implementation remains gated and must not be inferred as complete from B2.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/pivot-stage5a-b2-production-acceptance.md`
- blocking_items: none
- follow_up_required: yes
