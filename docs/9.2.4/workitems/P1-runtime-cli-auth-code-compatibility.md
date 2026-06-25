---
doc_role: workitem
doc_purpose: Track Runtime CLI compatibility with Runtime API auth-code mode.
version: 9.2.4
target: P1-runtime-cli-auth-code-compatibility
status: signed-off
created_at: 2026-06-25
updated_at: 2026-06-25
owner_modules:
  - foggy-runtime-cli
  - foggy-runtime-api
priority: P1
source_type: code-review
reproduction_status: confirmed-by-review
test_strategy: unit-test
automation_decision: required
---

# P1 Runtime CLI Auth-Code Compatibility

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent / reviewer / signoff-owner
- purpose: Record and track the CLI compatibility fix required for Runtime API auth-code mode.

## Background

Runtime API management write operations can require the existing shared auth code through `X-Foggy-Runtime-Code` or `Authorization: Bearer`. The current CLI client sends base URL, namespace, timeout, JSON body, and `X-NS`, but does not expose a way to pass the Runtime API auth code.

For customers using the CLI against a runtime with `foggy.runtime-api.auth-code` configured, read/query commands keep working, but management commands such as bundle add/update/remove, datasource add/test/bind, resources save, models validate, and models refresh are rejected with `RUNTIME_AUTH_REQUIRED`.

## Target Outcome

- CLI supports a global `--auth-code` option.
- CLI supports `FOGGY_RUNTIME_API_AUTH_CODE`.
- CLI sends the code using `X-Foggy-Runtime-Code`, matching the current Runtime API contract.
- Existing namespace, base URL, timeout, JSON body, error handling, and capability preflight behavior remain compatible.
- README documents the customer impact and usage.

## Impact Assessment

| Customer Runtime Mode | CLI Impact Before Fix | Expected After Fix |
|---|---|---|
| `none-dev-test-only` with no auth code | No impact | No impact |
| `auth-code` with configured auth code | Protected management commands fail without a code | Commands work when `--auth-code` or `FOGGY_RUNTIME_API_AUTH_CODE` is provided |
| `auth-code` without configured server code | Server fail-closes with `RUNTIME_AUTH_CODE_NOT_CONFIGURED` | Unchanged server behavior |

## Acceptance Criteria

- [x] `RuntimeApiClient` can carry an auth code and sends `X-Foggy-Runtime-Code`.
- [x] CLI global `--auth-code` passes the code into the client.
- [x] `FOGGY_RUNTIME_API_AUTH_CODE` is used when `--auth-code` is not provided.
- [x] Existing CLI client construction remains backward-compatible for tests and callers.
- [x] README explains auth-code usage and which operations need it.
- [x] CLI unit tests pass.

## Progress Tracking

| Item | Repo | Status | Notes |
|---|---|---|---|
| Version docs | foggy-data-mcp-bridge | done | 9.2.4 workitem created. |
| Test-first exposure | foggy-runtime-cli | done | Added failing tests for auth-code header and CLI/global env propagation before implementation. |
| CLI compatibility fix | foggy-runtime-cli | done | Added client auth-code field, parser option, env resolution, and README notes. |
| Verification | foggy-runtime-cli | done | Focused auth-code tests and full CLI test suite passed. |
| Quality / coverage writeback | foggy-data-mcp-bridge | done | Quality and coverage records written. |

## Test Evidence

| Evidence | Result |
|---|---|
| Pre-fix CLI auth-code propagation exposure | failed as expected: missing `RuntimeApiClient(auth_code=...)`, missing `--auth-code`, env var not propagated |
| Post-fix focused CLI tests | passed: 4/4 auth-code propagation tests |
| Full CLI tests | passed: 70/70 |

## Execution Check-In

- completed_work: CLI can pass Runtime API shared auth code through `X-Foggy-Runtime-Code` from either global `--auth-code` or `FOGGY_RUNTIME_API_AUTH_CODE`.
- tests_added_or_updated: `tests/test_client_http.py` covers auth header emission; `tests/test_cli.py` covers option, env fallback, and option-over-env precedence.
- changed_code: `RuntimeApiClient` carries optional `auth_code`; CLI parser resolves auth code and passes it to runtime client construction; README documents customer usage.
- compatibility: `none-dev-test-only` runtimes remain unchanged; `auth-code` runtimes require customers to provide the existing shared code for protected management commands; server fail-close behavior remains unchanged.
- experience: N/A, CLI/API behavior only.
- non_goals_preserved: no RBAC, user permission, audit, rotation, or security-layering change.

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-25
- acceptance_record: docs/9.2.4/acceptance/version-signoff.md
- blocking_items: none
- follow_up_required: no

## Non-Goals

- No server-side auth-code contract changes.
- No Runtime API protected-operation scope expansion.
- No RBAC, per-user permission, audit, or credential rotation implementation.
- No change to demo replay's trusted-local expectation that `securityMode=none-dev-test-only`.
