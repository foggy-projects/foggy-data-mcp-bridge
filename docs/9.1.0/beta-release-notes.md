---
doc_role: release_notes
doc_purpose: Beta release notes and release gating guidance for the 9.1.0 Pivot Engine phase closeout.
version: 9.1.0
target: Java Pivot Engine Beta
status: beta-ready-with-risks
created_at: 2026-05-02
---

# Foggy Pivot Engine 9.1.0 Beta Release Notes

## Release Position

9.1.0 is recommended for **beta** only. It should not be promoted to Release Candidate until the release owner either enables and verifies Stage 5A production transport, or explicitly defers it with updated acceptance criteria.

## Included in This Beta

- Release readiness workflow/script coverage for Pivot Java Core.
- Safe telemetry markers for SQL pushdown attempt, success, skip, fallback, domain-limit failure, and non-additive auxiliary query execution.
- MySQL 8 dialect capability detection for CTE/window-function SQL pushdown parity.
- Stage 5A large-domain transport design and internal renderer skeletons.
- Stage 5B Cascade Generate / multi-level TopN semantic design.
- Phase-level acceptance signoff with known risks.

## Not Enabled

- Stage 5A production domain transport is not wired into runtime query execution.
- `domain > 500` non-additive subtotal/grandTotal cases still fail closed in production paths.
- Cascade Generate implementation is not started.
- Tree advanced semantics and outer Pivot cache remain deferred.
- No public Pivot DSL changes are included.

## Verification Snapshot

| Gate | Status | Notes |
|---|---|---|
| SQLite release quick gate | PASS | `./scripts/verify-pivot-v9-release.ps1 -SkipFullRegression -SkipMcp` passed on 2026-05-02. |
| Full release readiness gate | PASS | `./scripts/verify-pivot-v9-release.ps1` passed on 2026-05-02, including full module regression, SQLite, MCP guardrail, MySQL8, and PostgreSQL parity. |
| Diff hygiene | PASS | `git diff --check` reported no diff hygiene errors after the phase commits. |
| Feature-level acceptance | PASS with risks | B1, B2-Prep, and C1 have acceptance records. |

## Known Risks

- Stage 5A production enablement still needs runtime MySQL 8.0.19+ version probing and safe renderer selection.
- MySQL 5.7 large-domain derived-table fallback must remain threshold guarded.
- C2 implementation will require careful CTE planner work and SQL size controls.
- Product telemetry should confirm real `domain > 500` demand before enabling Stage 5A production behavior.

## Upgrade Notes

- No public Pivot DSL migration is required.
- Existing fail-closed behavior for unsupported large-domain non-additive rollups is preserved.
- Consumers should treat new telemetry as operational visibility, not as a behavior change.

## RC Promotion Gate

The full release readiness script has passed once for this phase:

```powershell
./scripts/verify-pivot-v9-release.ps1
```

Before promoting to RC, release ownership still needs to decide whether B2 production transport is in scope for 9.1.0. If B2 is enabled before RC, add SQLite, MySQL8, PostgreSQL, and explicit MySQL 5.7 unsupported/fallback evidence for large-domain non-additive rollups.
