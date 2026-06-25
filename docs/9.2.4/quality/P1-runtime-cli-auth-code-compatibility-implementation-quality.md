---
doc_role: implementation_quality
doc_purpose: Implementation quality check for Runtime CLI auth-code compatibility.
version: 9.2.4
target: P1-runtime-cli-auth-code-compatibility
status: ready-for-coverage-audit
created_at: 2026-06-25
updated_at: 2026-06-25
owner_modules:
  - foggy-runtime-cli
---

# P1 Runtime CLI Auth-Code Compatibility Implementation Quality

## Scope

This check covers the `foggy-runtime-cli` change that lets customers pass the existing Runtime API shared auth code to protected management endpoints.

## Changed Surface

| Area | Change | Quality Notes |
|---|---|---|
| HTTP client | Added optional `auth_code` to `RuntimeApiClient` and sends `X-Foggy-Runtime-Code` only when present. | Narrow change; existing calls keep positional compatibility because the new field is last. |
| CLI parser | Added global `--auth-code`. | Matches existing global option pattern and keeps command-specific parsers unchanged. |
| Env resolution | Added `FOGGY_RUNTIME_API_AUTH_CODE` fallback, overridden by `--auth-code`. | Deterministic precedence; avoids changing base URL, namespace, timeout, or output behavior. |
| README | Documented auth-code usage and protected operation impact. | Customer-facing impact is explicit; no security layering promise added. |

## Risk Review

| Risk | Assessment |
|---|---|
| Existing CLI users without auth-code | Low. Header is absent unless configured; full CLI tests passed. |
| Auth-code leakage in output | Low. Code is not rendered in JSON or pretty output. Operators should prefer env vars for automation. |
| Server contract drift | Low. Implementation uses existing `X-Foggy-Runtime-Code` header accepted by Runtime API. |
| Demo replay semantics | Low. Existing replay still validates `securityMode=none-dev-test-only`; this change does not relax that guard. |

## Decision

- quality_decision: ready-for-coverage-audit
- blocking_items: none
- follow_up_required: no
