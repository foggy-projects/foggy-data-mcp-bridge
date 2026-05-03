---
doc_role: acceptance_record
doc_purpose: Final version-level acceptance signoff for the 9.1.0 Pivot Engine RC2 closeout.
acceptance_scope: version
version: 9.1.0
target: Foggy Pivot Engine 9.1.0 RC2
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex acceptance reviewer
signed_off_at: 2026-05-03
reviewed_by: Codex acceptance reviewer
blocking_items: []
follow_up_required: yes
evidence_count: 14
---

# Version Acceptance

## Background

This record signs off the 9.1.0 Pivot Engine RC2 closeout. The accepted scope includes release hardening, telemetry, Stage 5A production large-domain transport, Stage 5B C1/C1.1 cascade semantics, scoped Stage 5B C2 rows two-level cascade implementation, and LLM query tool routing guidance.

The acceptance standard is correctness-first and LLM-safe: when semantics or execution support cannot be proven, the engine must fail closed with explicit guidance rather than produce a best-effort result.

This is a repo-level release-candidate signoff. External package signing, artifact publication, deployment, and production telemetry review remain outside this repository acceptance.

## Acceptance Basis

- `docs/9.1.0/README.md`
- `docs/9.1.0/detailed_design/00_java_pivot_engine_roadmap.md`
- `docs/9.1.0/detailed_design/10_pivot_stage5a_domain_transport_plan.md`
- `docs/9.1.0/detailed_design/11_pivot_stage5b_cascade_generate_semantics.md`
- `docs/9.1.0/detailed_design/12_pivot_stage5b_cascade_generate_disambiguation.md`
- `docs/9.1.0/detailed_design/13_pivot_stage5b_c2_implementation_plan.md`
- `docs/9.1.0/detailed_design/14_llm_query_tool_capability_matrix.md`
- `docs/9.1.0/workitems/pivot-java-engine-9.1.0-followups.md`
- `docs/9.1.0/quality/pivot-stage5a-b2-production-quality.md`
- `docs/9.1.0/quality/pivot-stage5b-c2-cascade-generate-implementation-quality.md`
- `docs/9.1.0/test_coverage/pivot-stage5b-c2-cascade-generate-coverage-audit.md`
- `docs/9.1.0/acceptance/pivot-stage5a-b2-production-acceptance.md`
- `docs/9.1.0/acceptance/pivot-stage5b-c2-cascade-generate-acceptance.md`
- `docs/9.1.0/acceptance/rc-release-checklist.md`
- `docs/9.2.0/README.md`

## Checklist

- [x] 9.1.0 keeps the 9.0.0.beta public Pivot DSL boundary; no public MDX-compatible syntax expansion was accepted.
- [x] queryModel lifecycle is preserved for Pivot SQL pushdown, Stage 5A domain transport, and C2 cascade execution.
- [x] Stage 5A large-domain transport is production-wired behind dialect/version/threshold guards.
- [x] Stage 5A unsupported or unsafe paths fail closed instead of using unsafe in-memory fallback for large domains.
- [x] C1/C1.1 cascade semantics define ranking grain, Having-before-TopN order, deterministic tie-breaking, axis isolation, and refusal categories.
- [x] C2 v1 implements only rows two-level cascade with additive metrics, explicit `orderBy`, staged SQL execution, and deterministic tie-breaking.
- [x] C2 cascade requests never use the current memory fallback path.
- [x] Unsupported C2 semantic shapes and unproven dialects fail closed with `PIVOT_CASCADE_*` errors.
- [x] LLM routing guidance now distinguishes `query_model`, `timeWindow`, `pivot`, CTE, and internal DomainTransport responsibilities.
- [x] Remaining unresolved items are moved to `docs/9.2.0/README.md` and are not 9.1.0 blockers.

## Module Summary

| Area | Status | Decision | Notes |
|---|---|---|---|
| PIVOT-91-A1 | complete | accepted | CI/release readiness workflow and scripts are documented and runnable. |
| PIVOT-91-A2 | complete | accepted-with-risks | Safe telemetry markers exist; dashboards/log-query examples move to 9.2.0. |
| PIVOT-91-B1/B2 | signed-off | accepted-with-risks | Stage 5A domain transport is wired and verified on supported dialect evidence. |
| PIVOT-91-C1/C1.1 | signed-off | accepted-with-risks | Cascade semantics and LLM-safe disambiguation are accepted. |
| PIVOT-91-C2 | signed-off | accepted-with-risks | Rows two-level cascade is included in `v9.1.0-rc.2`; unsupported cases fail closed. |
| LLM tool routing | documented | accepted | `query_model`, `timeWindow`, `pivot`, and CTE selection guidance is documented. |
| PIVOT-91-D/E | deferred | moved to 9.2.0 | Tree + cascade and outer Pivot cache require separate 9.2.0 review. |
| Dialect evidence gaps | deferred | moved to 9.2.0 | SQL Server cascade and MySQL 5.7 live evidence remain fail-closed/deferred. |

## Evidence

| Evidence | Result | Notes |
|---|---|---|
| `v9.1.0-rc.2` tag at HEAD | PASS | Current C2-inclusive RC tag exists at commit `60d9be6e`. |
| Stage 5A full release gate | PASS | `./scripts/verify-pivot-v9-release.ps1` passed after B2 production enablement. |
| Stage 5A SQLite gate | PASS | `PivotSqlParityIntegrationTest`, `PivotIntegrationTest`, and `MetricAdditivityAnalyzerTest` passed under SQLite. |
| Stage 5A MySQL8 parity | PASS | `PivotSqlParityIntegrationTest#testLargeDomainTransportQueryModelParity` passed under `mysql8`. |
| Stage 5A PostgreSQL parity | PASS | The same large-domain parity test passed under `postgres`. |
| Domain renderer gate | PASS | `DomainRelationRendererTest` passed with 7 tests. |
| C2 validation/refusal | PASS | `PivotCascadeGenerateValidationTest` passed. |
| C2 staged SQL shape | PASS | `PivotAxisDomainSqlPlannerCascadeTest` and `PivotAxisDomainSqlPlannerTest` passed. |
| C2 SQL parity | PASS | `PivotCascadeGenerateSqlParityIntegrationTest` passed. |
| Parent-child TopN regression | PASS | `PivotIntegrationTest#testParentChildTopN` passed. |
| C2 quality gate | PASS with risks | C2 v1 is ready with fail-closed residual risks. |
| C2 coverage audit | READY with gaps | Gaps are fail-closed, deferred dialect coverage, or intentionally excluded semantics. |
| LLM capability matrix | PASS | Tool-routing guidance is documented without changing MCP tool descriptions. |
| Diff hygiene | PASS | `git diff --check` passes for the current doc-only closeout delta. |

## Risks / Open Items

- SQL Server cascade execution remains refused until 9.2.0 adds accepted SQL oracle and CI evidence. Functional impact: SQL Server users must simplify to single-level TopN or run C2 cascade on a supported dialect.
- MySQL 5.7 has no live large-domain parity profile in 9.1.0. Functional impact: C2 cascade remains unavailable on MySQL 5.7, and Stage 5A transport remains threshold-limited and fail-closed when renderer safety cannot be proven.
- Tree + cascade and advanced tree subtotal semantics are deferred to 9.2.0. Functional impact: tree-shaped "top children per parent" requests must be rewritten as normal two-level Pivot cascade where possible, or rejected.
- Outer Pivot cache is deferred to 9.2.0. Functional impact: correctness is unaffected, but repeated expensive Pivot queries may not get outer-cache acceleration.
- Production telemetry dashboard/log-query examples are deferred to 9.2.0. Functional impact: operators can use safe log markers, but standardized dashboards are not part of 9.1.0.

## Final Decision

The 9.1.0 Pivot Engine RC2 closeout is **accepted with risks**.

There are no blocking items for the accepted 9.1.0 scope. Remaining items are explicitly fail-closed or moved to the 9.2.0 follow-up plan.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-03
- acceptance_record: `docs/9.1.0/acceptance/version-signoff.md`
- blocking_items: none
- follow_up_required: yes
