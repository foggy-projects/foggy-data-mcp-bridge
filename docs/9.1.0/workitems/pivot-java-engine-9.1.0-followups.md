---
doc_role: workitem_index
doc_purpose: Track executable Java Pivot Engine follow-up items for 9.1.0.
version: 9.1.0
target: Java Pivot Engine Follow-Ups
status: proposed
created_at: 2026-05-02
---

# Pivot Java Engine 9.1.0 Follow-Ups

## Workitem Table

| ID | Priority | Item | Status | Owner | Exit Criteria |
|---|---|---|---|---|---|
| PIVOT-91-A1 | P0 | CI release readiness for Pivot Java Core | partial | Java Core / CI | SQLite PR gate and MySQL8/PostgreSQL release gate documented and runnable |
| PIVOT-91-A2 | P0 | Pushdown/fallback/domain-limit telemetry | partial | Java Core | Logs/metrics cover pushdown success, fallback reason, and `domain > 500` frequency |
| PIVOT-91-B1 | P1 | Stage 5A `DomainTransportPlan` design | proposed | Java Core | Design accepted with dialect matrix and queryModel lifecycle preservation |
| PIVOT-91-B2 | P1 | Stage 5A production implementation | gated | Java Core | Only starts after B1 and telemetry/product demand |
| PIVOT-91-C1 | P1 | Cascade Generate semantic design | proposed | Java Core / Product | Multi-level TopN semantics frozen with SQL oracle examples |
| PIVOT-91-C2 | P2 | Cascade Generate implementation | gated | Java Core | Only starts after C1 acceptance |
| PIVOT-91-D1 | P2 | Tree SQL TopN / tree subtotal feasibility | proposed | Java Core | Feasibility report with closure-table/descendant strategy decision |
| PIVOT-91-E1 | P2 | Outer Pivot cache feasibility | proposed | Java Core / Cache | Cache key and invalidation model accepted |

## Recommended Execution Order

1. PIVOT-91-A1: CI release readiness.
2. PIVOT-91-A2: telemetry.
3. PIVOT-91-B1: Stage 5A design.
4. Decide PIVOT-91-B2 based on telemetry or product need.
5. PIVOT-91-C1 before any advanced Generate implementation.
6. PIVOT-91-D1 and PIVOT-91-E1 after ranking semantics and telemetry are clearer.

## Guardrails

- No queryModel bypass.
- No public DSL changes without a separate requirement.
- No default-on Stage 5A implementation without cross-database parity.
- No Stage 5B implementation before semantics are accepted.
- Keep MySQL 5.7 behavior explicit: fallback, derived-table support, or unsupported must be documented per workitem.

## Progress Update - 2026-05-02

### PIVOT-91-A1 CI Release Readiness

Status: `partial`.

Implemented:

- `.github/workflows/release.yml` now runs `scripts/verify-pivot-v9-release.sh --skip-external-db` before the release JAR is built.
- The release build, Docker publish, and GitHub release jobs are now blocked if the local release readiness suite fails.

Remaining:

- Add an environment-backed MySQL8/PostgreSQL release gate in CI, or document the external database parity step as a required manual release checklist item.
- Decide whether the full regression step is acceptable on every tag release, or whether a separate PR gate should run the SQLite-only subset.

### PIVOT-91-A2 Pushdown/Fallback/Domain-Limit Telemetry

Status: `partial`.

Implemented:

- SQL pushdown success already logs returned row count.
- SQL pushdown fallback now logs structured `reason`, `model`, and `fallback=memory`.
- SQL pushdown skip now logs a structured skip reason at debug level: disabled, queryFacade unavailable, tree mode, derived metric post-processing, or no axis having/limit.
- `domain > 500` non-additive rollup fail-closed now logs domain size, max allowed size, model, whether SQL pushdown was used, and row/column domain sizes before raising the user-facing exception.

Remaining:

- Promote these logs into counters/timers if the project standardizes a metrics registry.
- Add production dashboard or log-query examples after deployment telemetry is available.
