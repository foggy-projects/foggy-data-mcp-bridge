---
doc_role: release_readiness
doc_purpose: S13 release readiness checklist and repeatable verification entrypoint for Foggy Pivot 9.0.0.beta.
version: 9.0.0.beta
target: Pivot V9 release readiness
status: ready
decision: ready-for-release-candidate
owner: Codex
updated_at: 2026-05-01
blocking_items: []
follow_up_required: yes
---

# S13 Release Readiness

## Scope

S13 does not add new Pivot DSL semantics. Its goal is to turn the signed-off Java Engine and MCP Schema work into a repeatable release-candidate verification package.

Covered:

- Full module regression entrypoint.
- SQLite / MySQL8 / PostgreSQL Pivot SQL Parity entrypoint.
- MCP Schema and JSON-RPC guardrail entrypoint.
- Documentation consistency for S11 `parentShare` and S12 `baselineRatio`.

Out of scope:

- Python Mirror implementation.
- New public DSL for `CELL_AT`, `AXIS_MEMBER`, `ROLLUP_TO`, or cascaded Generate.
- Performance tuning beyond current UNION ALL fallback behavior.

## Verification Entrypoint

Windows / PowerShell:

```powershell
.\scripts\verify-pivot-v9-release.ps1
```

Linux / macOS / Git Bash:

```bash
./scripts/verify-pivot-v9-release.sh
```

Fast local run without external Docker databases:

```powershell
.\scripts\verify-pivot-v9-release.ps1 -SkipExternalDb
```

The full release-candidate run executes:

```powershell
mvn test -P!multi-db
mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest" "-Dspring.profiles.active=sqlite" "-P!multi-db"
mvn test -pl foggy-dataset-mcp "-Dtest=PivotSchemaValidationTest,AnalystMcpControllerTest" "-P!multi-db"
mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest" "-Dspring.profiles.active=mysql8" "-P!multi-db"
mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest" "-Dspring.profiles.active=postgres" "-P!multi-db"
```

## External Database Requirements

S13 parity target databases:

| Profile | Required container | Expected engine | Purpose |
|---|---|---|---|
| `sqlite` | none | embedded SQLite | local deterministic baseline |
| `mysql8` | `foggy-demo-mysql8` | MySQL 8.0 | MySQL window-function parity oracle |
| `postgres` | `foggy-demo-postgres` | PostgreSQL 15 | PostgreSQL ordering and dialect parity |

`docker` / MySQL 5.7 is a legacy profile and is not a S12/S13 baselineRatio parity target. MySQL 5.7 lacks the window-function support used by the independent SQL oracle, so it must not be used as evidence for MySQL8 parity.

## Checklist

- [x] S12 `baselineRatio` has a formal acceptance record.
- [x] `version-signoff.md` records SQLite / MySQL8 / PostgreSQL parity as signed-off evidence.
- [x] `README.md` points to the repeatable release verification scripts.
- [x] `mdx_vs_foggy_syntax_comparison.md` reflects the 92%+ coverage conclusion after S12.
- [x] Public Prompt / Schema keep `CELL_AT`, `AXIS_MEMBER`, and `ROLLUP_TO` fail-closed.
- [x] Python Mirror remains explicitly out of Java Engine release scope.

## Known Non-Blocking Follow-Ups

- Add CI wiring for `scripts/verify-pivot-v9-release.sh` after the CI environment can provision MySQL8 and PostgreSQL containers.
- Keep Python Mirror work under `docs/9.0.0.beta/acceptance/s10_python_parity_plan.md`.
- Consider a future optimizer pass for baselineRatio SQL pushdown, without changing the public DSL contract.

## Signoff Marker

- release_readiness_status: ready
- release_readiness_decision: ready-for-release-candidate
- verification_entrypoint: scripts/verify-pivot-v9-release.ps1
- blocking_items: none
- follow_up_required: yes
