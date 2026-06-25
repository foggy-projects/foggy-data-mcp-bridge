---
acceptance_scope: version
version: 9.2.4
target: Foggy Java Engine 9.2.4 Runtime CLI auth-code compatibility
doc_role: acceptance-record
doc_purpose: Record formal version acceptance and signoff decision for 9.2.4.
status: signed-off
decision: accepted
signed_off_by: codex
signed_off_at: 2026-06-25
reviewed_by: codex
blocking_items: []
follow_up_required: no
evidence_count: 4
---

# Version Acceptance

## Document Purpose

- doc_type: acceptance
- intended_for: signoff-owner / reviewer / root-controller
- purpose: Record the formal 9.2.4 acceptance decision and evidence summary.

## Background

- Version: 9.2.4
- Scope: Runtime CLI compatibility with Runtime API `auth-code` mode.
- Goal: Let customers using `foggy-runtime-cli` pass the existing shared Runtime API auth code for protected management operations without changing the server auth contract.
- Boundary: No Runtime API auth-code contract change, RBAC, audit, user permission model, credential rotation, protected-operation scope expansion, or security layering change.

## Acceptance Basis

- `docs/9.2.4/README.md`
- `docs/9.2.4/workitems/P1-runtime-cli-auth-code-compatibility.md`
- `docs/9.2.4/quality/P1-runtime-cli-auth-code-compatibility-implementation-quality.md`
- `docs/9.2.4/coverage/P1-runtime-cli-auth-code-compatibility-coverage-audit.md`

## Module Summary

| Module | Owner | Status | Acceptance Record | Notes |
|---|---|---|---|---|
| `foggy-runtime-cli` | codex | signed-off | `docs/9.2.4/acceptance/version-signoff.md` | CLI auth-code option, env fallback, header propagation, tests, and README accepted. |

## Checklist

- [x] Scope module has completed feature-level readiness through workitem, quality gate, and coverage audit.
- [x] Workitem acceptance criteria are covered.
- [x] Test records are complete and traceable.
- [x] Experience verification is `N/A` because this is CLI/API behavior with no UI surface.
- [x] Customer impact is documented for `none-dev-test-only`, configured `auth-code`, and misconfigured `auth-code` runtimes.
- [x] Guardrails were preserved: no security layering, RBAC, user permissions, audit, rotation, or Runtime API auth-code contract change.

## Evidence

- Test:
  - Pre-fix auth-code propagation tests failed as expected: missing `RuntimeApiClient(auth_code=...)`, missing `--auth-code`, and env var not propagated.
  - `$env:PYTHONPATH='src'; python -m pytest tests/test_client_http.py::RuntimeApiClientHttpTest::test_request_sends_runtime_auth_code_header tests/test_cli.py::CliTest::test_auth_code_option_is_passed_to_client tests/test_cli.py::CliTest::test_auth_code_env_is_passed_to_client tests/test_cli.py::CliTest::test_auth_code_option_overrides_env` passed, 4 tests.
  - `$env:PYTHONPATH='src'; python -m pytest` passed, 70 tests.
- Experience:
  - N/A. No UI or user interaction surface was changed.
- Delivery Artifacts:
  - `RuntimeApiClient`: carries optional `auth_code` and sends `X-Foggy-Runtime-Code` when present.
  - CLI parser: supports global `--auth-code` and `FOGGY_RUNTIME_API_AUTH_CODE`, with option-over-env precedence.
  - README: documents usage, protected management operations, and customer impact.

## Blocking Items

- none

## Risks / Open Items

- No live Runtime API auth-code integration test was run. This is non-blocking because the change is a CLI-side header propagation compatibility fix and unit tests verify the documented server contract path.
- Customers using `auth-code` mode need a CLI version containing this change and must provide the shared code for protected management operations.

## Final Decision

Decision: `accepted`.

9.2.4 satisfies the documented Runtime CLI auth-code compatibility scope. Required quality and coverage records exist, focused auth-code tests and the full CLI test suite passed, customer impact is documented, and no blocking gaps remain for this iteration.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-25
- acceptance_record: docs/9.2.4/acceptance/version-signoff.md
- blocking_items: none
- follow_up_required: no
