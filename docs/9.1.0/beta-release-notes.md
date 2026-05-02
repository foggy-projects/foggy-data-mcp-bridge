---
doc_role: release_notes
doc_purpose: Release candidate notes and release gating guidance for the 9.1.0 Pivot Engine phase closeout.
version: 9.1.0
target: Java Pivot Engine Release Candidate
status: release-candidate-with-risks
created_at: 2026-05-02
---

# Foggy Pivot Engine 9.1.0 Release Candidate Notes

## Release Position

9.1.0 is recommended for **release candidate** status after the Stage 5A B2 production transport hook passed the full Pivot V9 release readiness gate. It is not a GA publication record; package signing, external release ownership, and production telemetry review remain outside this repo-level closeout.

## Included in This RC

- Release readiness workflow/script coverage for Pivot Java Core.
- Safe telemetry markers for SQL pushdown attempt, success, skip, fallback, domain-limit failure, and non-additive auxiliary query execution.
- MySQL 8 dialect capability detection for CTE/window-function SQL pushdown parity.
- Stage 5A large-domain transport design, internal renderers, and production queryModel wiring.
- Supported `domain > 500` non-additive subtotal/grandTotal cases now use safe domain transport instead of unconditional fail-closed behavior.
- Stage 5B Cascade Generate / multi-level TopN semantic design.
- Phase-level acceptance signoff with known risks.

## Still Gated or Deferred

- Cascade Generate implementation is not started.
- Tree advanced semantics and outer Pivot cache remain deferred.
- No public Pivot DSL changes are included.
- Unsupported dialects, unsafe renderer thresholds, and MySQL 8 versions before 8.0.19 still fail closed for large-domain transport.

## Verification Snapshot

| Gate | Status | Notes |
|---|---|---|
| SQLite release quick gate | PASS | `./scripts/verify-pivot-v9-release.ps1 -SkipFullRegression -SkipMcp` passed on 2026-05-02. |
| Full release readiness gate | PASS | `./scripts/verify-pivot-v9-release.ps1` passed on 2026-05-02, including full module regression, SQLite, MCP guardrail, MySQL8, and PostgreSQL parity. |
| Stage 5A SQLite large-domain parity | PASS | `PivotSqlParityIntegrationTest#testLargeDomainTransportQueryModelParity` passed under `sqlite`. |
| Stage 5A MySQL8 large-domain parity | PASS | The same parity test passed under `mysql8`. |
| Stage 5A PostgreSQL large-domain parity | PASS | The same parity test passed under `postgres`. |
| Diff hygiene | PASS | `git diff --check` reported no diff hygiene errors for the RC delta. |
| Feature-level acceptance | PASS with risks | B1, B2-Prep, B2 production, and C1 have acceptance records. |

## Known Risks

- Operational thresholds for Stage 5A transport should be revisited after production telemetry confirms real domain sizes.
- MySQL 5.7 large-domain derived-table fallback remains threshold guarded and may still refuse very large domains.
- C2 implementation will require careful CTE planner work and SQL size controls.
- Product telemetry should confirm sustained `domain > 500` demand before considering GA hardening or wider public messaging.

## Upgrade Notes

- No public Pivot DSL migration is required.
- Existing fail-closed behavior for unsupported or unsafe large-domain non-additive rollups is preserved.
- Consumers should treat new telemetry as operational visibility for pushdown and transport behavior.

## GA Promotion Gate

The full release readiness script has passed once for this phase:

```powershell
./scripts/verify-pivot-v9-release.ps1
```

Before promoting beyond RC, release ownership should confirm external packaging/signing, review production telemetry thresholds, and decide whether C2 remains explicitly out of scope for the 9.1.0 GA line.
