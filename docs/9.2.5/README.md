---
doc_role: version_followup_plan
doc_purpose: Track 9.2.5 Runtime CLI release closure and Runtime API auth-code technical boundary evaluation.
version: 9.2.5
target: Runtime API Auth-Code CLI Release and Boundary Evaluation
status: released
created_at: 2026-06-25
updated_at: 2026-06-25
---

# Foggy Runtime API 9.2.5 Follow-Ups

## Purpose

9.2.5 closes the customer-facing delivery gap for Runtime API auth-code mode by publishing a CLI release that can send the existing shared auth code. It also records the agreed technical security boundary: this is an internal technical gate, not a customer permission model.

## Current Scope

| Area | Workitem | Status | Owner Module | Boundary |
|---|---|---|---|---|
| CLI release closure | `workitems/P1-runtime-cli-auth-code-release-closure.md` | released | `foggy-runtime-cli` | CLI `0.1.6` released with auth-code support and live Runtime API evidence. |
| Auth-code boundary evaluation | `workitems/P1-runtime-api-auth-code-security-boundary-evaluation.md` | evaluated | `foggy-runtime-api`, `foggy-runtime-cli` | Shared auth code is acceptable for the current non-customer-facing management gate; no RBAC, audit, rotation, or security layering is introduced. |

## Verification Summary

| Evidence | Result |
|---|---|
| `powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1 -Clean` in `foggy-runtime-cli` | passed, 70/70 tests, built `0.1.6` wheel and sdist |
| Live Runtime API auth-code check with CLI `0.1.6` wheel | passed, 6/6 checks |
| Public CLI release `v0.1.6` | published and verified at `https://github.com/foggy-projects/foggy-runtime-cli/releases/tag/v0.1.6` |
| Public wheel install smoke | passed; installed `foggy-runtime-cli==0.1.6` from GitHub Release and verified CLI help includes `--auth-code` |

## Evidence Records

- implementation_quality: `quality/P1-runtime-cli-auth-code-release-closure-implementation-quality.md`
- coverage_audit: `coverage/P1-runtime-cli-auth-code-release-closure-coverage-audit.md`
- boundary_evaluation: `workitems/P1-runtime-api-auth-code-security-boundary-evaluation.md`

## Customer Impact

| Customer Runtime Mode | Impact |
|---|---|
| `none-dev-test-only` without an auth code | No behavior change. Existing CLI commands continue to work without `--auth-code`. |
| `auth-code` with a configured server code | Customers using protected management operations need CLI `0.1.6` or later and must provide `--auth-code <code>` or `FOGGY_RUNTIME_API_AUTH_CODE`. |
| Read/query/script execution commands | No new CLI requirement from this iteration; these endpoints remain outside the management auth-code gate by design. |

## Guardrails

- Do not change the Runtime API auth-code server contract.
- Do not add RBAC, per-user permission, audit, credential rotation, or security layering.
- Prefer `FOGGY_RUNTIME_API_AUTH_CODE` for automation to reduce command-line exposure.
- Treat auth-code as sufficient only for the current internal technical interface, not as a public/customer security model.
