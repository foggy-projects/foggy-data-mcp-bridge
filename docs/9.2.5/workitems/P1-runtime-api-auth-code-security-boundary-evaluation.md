---
doc_role: risk_evaluation
doc_purpose: Record Runtime API auth-code technical security boundary evaluation.
version: 9.2.5
target: P1-runtime-api-auth-code-security-boundary-evaluation
status: evaluated
created_at: 2026-06-25
updated_at: 2026-06-25
owner_modules:
  - foggy-runtime-api
  - foggy-runtime-cli
  - foggy-fsscript
priority: P1
source_type: technical-risk-review
---

# P1 Runtime API Auth-Code Security Boundary Evaluation

## Decision Context

The agreed product boundary is technical responsibility only. This Runtime API surface is not being treated as a customer-facing permission system. The required control is a shared authorization code that protects management operations when the runtime is configured for `securityMode=auth-code`.

The previous 9.2.1 closure already locked the protected management inventory and explicitly left query/read/script execution outside this gate. This 9.2.5 evaluation records whether that boundary is acceptable after the CLI release work.

## Boundary Decision

- decision: acceptable-for-current-internal-technical-interface
- reason: configured Runtime API management operations fail closed without the shared auth code, and CLI `0.1.6` can provide the same code through the documented contract.
- non_customer_facing_security_model: yes
- require_complex_permission_model: no
- require_runtime_code_change_now: no

## Protected Management Surface

The existing auth-code gate protects Runtime API management operations such as:

- bundle add, update, remove
- datasource add, update, remove, test
- namespace datasource bind
- resources save
- models validate
- models refresh
- legacy FSScript bundle add/remove when the runtime-api interceptor is active

Primary source record:

- `docs/9.2.1/workitems/P1-runtime-api-auth-code-technical-closure.md`

## Intentionally Open Or Out Of Scope

These remain outside the auth-code management gate by design:

- capabilities discovery
- bundle/datasource read and model metadata discovery
- dataset query validate/execute
- SQL/table read probes
- compose validate/preview/execute
- fsscript execution
- resource export/read paths

This is not changed by CLI `0.1.6`.

## Risk Register

| Risk | Assessment | Current Handling |
|---|---|---|
| Shared secret has no per-user attribution | Accepted for current internal technical boundary | No RBAC/audit work in scope. |
| Auth code may leak through shell history or process lists when passed as `--auth-code` | Residual operational risk | README and release docs support `FOGGY_RUNTIME_API_AUTH_CODE`; automation should prefer env var. |
| No rotation or secret lifecycle management | Accepted by scope | Not required for this iteration. |
| Query/read/script endpoints are not gated by auth-code | Accepted by scope | Recorded as deliberate non-goal in 9.2.1 and 9.2.5. |
| Transport security is not enforced by CLI | Deployment responsibility | Use trusted local network or HTTPS gateway for non-local deployments. |
| Standalone FSScript-only applications could expose legacy `/api/bundles/**` without runtime-api interceptor | Known boundary | Keep runtime-api integration as the protected path; deployment must protect explicitly re-enabled legacy endpoints. |

## CLI Release Impact

CLI `0.1.6` reduces the practical operational gap for `auth-code` runtimes because protected management operations can now pass the shared code without custom HTTP tooling.

It does not make auth-code a complete customer-facing security model. It only makes the already-agreed shared-code management gate usable through the official CLI.

## Evidence

| Evidence | Result |
|---|---|
| 9.2.1 management inventory unit tests | passed in previous closure; protected surface locked by `RuntimeApiAuthCodeGateTest`. |
| CLI `0.1.6` full release build | passed, 70/70 tests. |
| CLI `0.1.6` live Runtime API auth-code verification | passed, no-code rejected and option/env code accepted. |
| Public CLI `v0.1.6` release | published and install-smoked from GitHub Release. |

Live evidence:

- `D:/foggy-projects/foggy-data-mcp/.codex-tmp/runtime-cli-auth-code-live/20260625-163456/summary.json`

## Recommendation

- CLI `0.1.6` release closure is complete.
- Keep the current auth-code boundary as-is.
- Do not start a security layering or permission-model redesign in this iteration.
- Prefer operational documentation over new code for the residual risks above.
