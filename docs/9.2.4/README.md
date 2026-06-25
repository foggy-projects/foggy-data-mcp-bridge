---
doc_role: version_followup_plan
doc_purpose: Track 9.2.4 Runtime API auth-code CLI compatibility work.
version: 9.2.4
target: Java Engine 9.2.4 Follow-Up Roadmap
status: signed-off
created_at: 2026-06-25
updated_at: 2026-06-25
---

# Foggy Java Engine 9.2.4 Follow-Ups

## Purpose

9.2.4 closes the CLI compatibility gap introduced by Runtime API auth-code management protection. It keeps the server-side security model unchanged and adds only the client-side ability to pass the existing shared auth code.

## Current Scope

| Area | Workitem | Status | Owner Module | Boundary |
|---|---|---|---|---|
| Runtime CLI auth-code compatibility | `workitems/P1-runtime-cli-auth-code-compatibility.md` | signed-off | `foggy-runtime-cli` | CLI can pass the existing Runtime API auth code through the supported header; no RBAC, audit, user permission, or security layering change. |

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-25
- acceptance_record: docs/9.2.4/acceptance/version-signoff.md
- blocking_items: none
- follow_up_required: no

## Verification Summary

| Evidence | Result |
|---|---|
| `python -m pytest tests/test_client_http.py::RuntimeApiClientHttpTest::test_request_sends_runtime_auth_code_header tests/test_cli.py::CliTest::test_auth_code_option_is_passed_to_client tests/test_cli.py::CliTest::test_auth_code_env_is_passed_to_client tests/test_cli.py::CliTest::test_auth_code_option_overrides_env` | passed, 4/4 |
| `python -m pytest` in `foggy-runtime-cli` with `PYTHONPATH=src` | passed, 70/70 |

## Customer Impact

| Customer Runtime Mode | Impact |
|---|---|
| `none-dev-test-only` without an auth code | No behavior change. Existing CLI commands continue to work without `--auth-code`. |
| `auth-code` with a configured server code | Customers need a CLI version containing this change and must provide `--auth-code <code>` or `FOGGY_RUNTIME_API_AUTH_CODE` for protected management operations. |
| `auth-code` without a configured server code | Unchanged fail-close behavior; the server still rejects protected operations with configuration error. |

## Evidence Records

- implementation_quality: `quality/P1-runtime-cli-auth-code-compatibility-implementation-quality.md`
- coverage_audit: `coverage/P1-runtime-cli-auth-code-compatibility-coverage-audit.md`

## Guardrails

- Do not change Runtime API auth-code contract.
- Do not introduce RBAC, user permissions, audit, credential rotation, or security layering.
- Preserve CLI behavior for runtimes using `none-dev-test-only`.
- Keep demo replay trusted-local semantics intact.
