---
doc_role: coverage_audit
doc_purpose: Test coverage audit for Runtime CLI auth-code compatibility.
version: 9.2.4
target: P1-runtime-cli-auth-code-compatibility
status: ready-for-acceptance
created_at: 2026-06-25
updated_at: 2026-06-25
owner_modules:
  - foggy-runtime-cli
---

# P1 Runtime CLI Auth-Code Compatibility Coverage Audit

## Requirement Mapping

| Requirement | Evidence | Result |
|---|---|---|
| `RuntimeApiClient` sends the existing Runtime API shared auth code through `X-Foggy-Runtime-Code`. | `tests/test_client_http.py::RuntimeApiClientHttpTest::test_request_sends_runtime_auth_code_header` | covered, passed |
| CLI global `--auth-code` reaches the runtime client. | `tests/test_cli.py::CliTest::test_auth_code_option_is_passed_to_client` | covered, passed |
| `FOGGY_RUNTIME_API_AUTH_CODE` is used when option is absent. | `tests/test_cli.py::CliTest::test_auth_code_env_is_passed_to_client` | covered, passed |
| Global option overrides env var. | `tests/test_cli.py::CliTest::test_auth_code_option_overrides_env` | covered, passed |
| Existing CLI behavior remains compatible. | Full `python -m pytest` in `foggy-runtime-cli` | covered, passed 70/70 |

## Test Evidence

| Command | Result |
|---|---|
| `$env:PYTHONPATH='src'; python -m pytest tests/test_client_http.py::RuntimeApiClientHttpTest::test_request_sends_runtime_auth_code_header tests/test_cli.py::CliTest::test_auth_code_option_is_passed_to_client tests/test_cli.py::CliTest::test_auth_code_env_is_passed_to_client tests/test_cli.py::CliTest::test_auth_code_option_overrides_env` | passed, 4/4 |
| `$env:PYTHONPATH='src'; python -m pytest` | passed, 70/70 |

## Coverage Decision

- coverage_status: sufficient-for-acceptance
- blocking_items: none
- residual_risk: No live Runtime API auth-code integration test was run; unit coverage verifies CLI header emission and command parsing against the documented server contract.
- next_step: formal acceptance signoff when requested.
