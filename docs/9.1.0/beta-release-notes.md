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

## Included in `v9.1.0-rc.1`

- Release readiness workflow/script coverage for Pivot Java Core.
- Safe telemetry markers for SQL pushdown attempt, success, skip, fallback, domain-limit failure, and non-additive auxiliary query execution.
- MySQL 8 dialect capability detection for CTE/window-function SQL pushdown parity.
- Stage 5A large-domain transport design, internal renderers, and production queryModel wiring.
- Supported `domain > 500` non-additive subtotal/grandTotal cases now use safe domain transport instead of unconditional fail-closed behavior.
- Stage 5B Cascade Generate / multi-level TopN semantic design.
- Phase-level acceptance signoff with known risks.

## After `v9.1.0-rc.1`

PIVOT-91-C2 Cascade Generate implementation was completed and feature-signed-off after the `v9.1.0-rc.1` tag point. If release ownership wants C2 in the 9.1.0 release line, commit the C2 changes and cut a new RC tag. Do not treat the existing `v9.1.0-rc.1` tag as containing C2 implementation.

## Still Gated or Deferred for C2 v1

- SQL Server cascade execution is rejected/deferred.
- Conservative MySQL / MySQL 5.7 cascade execution is rejected/deferred.
- Tree mode, cross-axis cascade, three-level cascade, having-only cascade, and non-additive cascade totals remain rejected/deferred.
- Tree advanced semantics and outer Pivot cache remain deferred.
- No public Pivot DSL changes are included.
- Unsupported dialects, unsafe renderer thresholds, and MySQL 8 versions before 8.0.19 still fail closed for large-domain transport.

## Verification Snapshot

| Gate | Status | Notes |
|---|---|---|
| SQLite release quick gate | PASS | `./scripts/verify-pivot-v9-release.ps1 -SkipFullRegression -SkipMcp` passed on 2026-05-02. |
| Full release readiness gate | PASS | `./scripts/verify-pivot-v9-release.ps1` passed on 2026-05-02, including full module regression, SQLite, MCP guardrail, MySQL8, and PostgreSQL parity. |
| Post-commit RC final gate | PASS | `./scripts/verify-pivot-v9-release.ps1` passed after B2 commit `5992e246`. |
| Stage 5A SQLite large-domain parity | PASS | `PivotSqlParityIntegrationTest#testLargeDomainTransportQueryModelParity` passed under `sqlite`. |
| Stage 5A MySQL8 large-domain parity | PASS | The same parity test passed under `mysql8`. |
| Stage 5A PostgreSQL large-domain parity | PASS | The same parity test passed under `postgres`. |
| Diff hygiene | PASS | `git diff --check` reported no diff hygiene errors for the RC delta. |
| Feature-level acceptance | PASS with risks | B1, B2-Prep, B2 production, and C1 have acceptance records for `v9.1.0-rc.1`; C2 has a later separate `accepted-with-risks` feature signoff. |

## Known Risks

- Operational thresholds for Stage 5A transport should be revisited after production telemetry confirms real domain sizes.
- MySQL 5.7 large-domain derived-table fallback remains threshold guarded and may still refuse very large domains.
- C2 v1 remains scoped to rows two-level cascade only; unsupported semantic shapes fail closed.
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

Before promoting beyond RC, release ownership should confirm external packaging/signing, review production telemetry thresholds, and decide whether the later C2 implementation is included through a new RC or remains explicitly out of scope for the 9.1.0 GA line.
