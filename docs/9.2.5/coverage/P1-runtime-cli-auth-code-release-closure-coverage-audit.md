---
doc_role: coverage_audit
doc_purpose: Test coverage audit for Runtime CLI auth-code release closure.
version: 9.2.5
target: P1-runtime-cli-auth-code-release-closure
status: passed
created_at: 2026-06-25
updated_at: 2026-06-25
owner_modules:
  - foggy-runtime-cli
  - foggy-runtime-api
---

# P1 Runtime CLI Auth-Code Release Closure Coverage Audit

## Requirement Mapping

| Requirement | Evidence | Result |
|---|---|---|
| Release artifact build runs full CLI tests. | `powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1 -Clean` | covered, passed 70/70 |
| CLI package version is `0.1.6`. | `dist/release-manifest.json`, isolated venv import check | covered, `cliVersion=0.1.6` |
| Auth-code runtime reports `securityMode=auth-code`. | live check `auth-capabilities` | covered, passed |
| Auth-code runtime rejects protected management operation without code. | live check `auth-bundles-add-no-code` | covered, `RUNTIME_AUTH_REQUIRED` |
| Auth-code runtime accepts global `--auth-code`. | live check `auth-bundles-add-option-code` | covered, passed |
| Auth-code runtime accepts `FOGGY_RUNTIME_API_AUTH_CODE`. | live check `auth-bundles-add-env-code` | covered, passed |
| Default runtime remains compatible without auth code. | live checks `default-capabilities`, `default-bundles-add-no-code` | covered, passed |
| Public release assets are available to customers. | GitHub Release `v0.1.6`, public wheel install smoke | covered, passed |

## Test Evidence

| Command / Evidence | Result |
|---|---|
| `powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1 -Clean` in `D:/foggy-projects/foggy-data-mcp/foggy-runtime-cli` | passed, 70/70 |
| `D:/foggy-projects/foggy-data-mcp/foggy-runtime-cli/dist/release-manifest.json` | version `0.1.6`, wheel and sdist checksums recorded |
| `D:/foggy-projects/foggy-data-mcp/.codex-tmp/runtime-cli-auth-code-live/20260625-163456/summary.json` | passed, 6/6 live checks |
| `https://github.com/foggy-projects/foggy-runtime-cli/releases/tag/v0.1.6` | published with wheel, sdist, install scripts, `SHA256SUMS`, and `release-manifest.json` |
| Public wheel install smoke from GitHub Release | passed; `foggy-runtime --help` includes `--auth-code` |

## Coverage Decision

- coverage_status: sufficient-for-release
- blocking_items: none
- residual_risk: none blocking; security boundary residual risks are tracked in the boundary evaluation doc.
- next_step: continue with the agreed security boundary review, without adding a permission-layer redesign.
