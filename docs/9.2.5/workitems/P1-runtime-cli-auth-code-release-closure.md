---
doc_role: workitem
doc_purpose: Track Runtime CLI auth-code release closure.
version: 9.2.5
target: P1-runtime-cli-auth-code-release-closure
status: released
created_at: 2026-06-25
updated_at: 2026-06-25
owner_modules:
  - foggy-runtime-cli
  - foggy-runtime-api
priority: P1
source_type: release-closure
reproduction_status: confirmed-by-live-verification
test_strategy: unit-test-plus-live-runtime
automation_decision: required
---

# P1 Runtime CLI Auth-Code Release Closure

## Background

9.2.4 added CLI support for the existing Runtime API shared auth code, but public CLI release inventory showed that `foggy-projects/foggy-runtime-cli` already had releases through `v0.1.5`, and `v0.1.5` did not contain the auth-code implementation.

This iteration moves the deliverable to CLI `0.1.6`, keeps the server-side auth-code contract unchanged, and verifies the release wheel against a live Java Runtime API launcher.

## Target Outcome

- Release CLI `0.1.6` as the next public version after `v0.1.5`.
- Include existing auth-code support:
  - global `--auth-code`
  - `FOGGY_RUNTIME_API_AUTH_CODE`
  - `X-Foggy-Runtime-Code` request header
- Keep `none-dev-test-only` Runtime API compatibility unchanged.
- Add live verification evidence for `auth-code` and default runtime modes.
- Keep release notes clear that this is not RBAC, audit, rotation, or a customer permission model.

## Progress Tracking

| Item | Repo | Status | Notes |
|---|---|---|---|
| Public release inventory | `foggy-runtime-cli` GitHub repo | done | Public latest before this work was `v0.1.5`; `v0.1.6` selected as next release target. |
| Version bump | `foggy-data-mcp/foggy-runtime-cli` | done | `pyproject.toml`, `__init__.py`, installers, README moved to `0.1.6`. |
| Public repo release metadata sync | `foggy-data-mcp/foggy-runtime-cli` | done | Brought release workflow and demo failure issue template up to public repo baseline, then added auth-code release-note wording. |
| Release build | `foggy-data-mcp/foggy-runtime-cli` | done | Build produced wheel, sdist, `SHA256SUMS`, and `release-manifest.json`. |
| Live Runtime API verification | workspace `.codex-tmp` | done | CLI `0.1.6` wheel verified against Java launcher in `auth-code` and `none-dev-test-only` modes. |
| Public release publish | `foggy-projects/foggy-runtime-cli` | done | Published `v0.1.6` at `https://github.com/foggy-projects/foggy-runtime-cli/releases/tag/v0.1.6`; workflow run `28157777945` passed. |
| Public wheel install smoke | GitHub Release asset | done | Installed `foggy-runtime-cli==0.1.6` from the public wheel and verified CLI help exposes `--auth-code`. |

## Execution Check-In

| Dimension | Status | Evidence |
|---|---|---|
| Development progress | complete | CLI `0.1.6` version metadata, auth-code option/env/header path, release workflow wording, README guidance, and public repo sync completed. |
| Testing progress | complete | Local release build passed 70/70 tests; live Runtime API auth-code verification passed 6/6 checks; public wheel install smoke passed. |
| Experience progress | N/A | Pure CLI/API release closure; no UI page, form, or interactive browser workflow changed. |
| Self-check | passed | Scope stayed limited to existing shared-code contract; no RBAC, audit, rotation, or protected-surface expansion was added. |

## Public Release Artifact Evidence

| Artifact | SHA256 | Bytes |
|---|---|---:|
| `foggy_runtime_cli-0.1.6-py3-none-any.whl` | `bace0b6568ae6a5774282c1ebbbea198bf88b284cdbf03810c771e5aa6f8e3ff` | 27083 |
| `foggy_runtime_cli-0.1.6.tar.gz` | `11efe70e6110e952eab1a034c8325e70a5490d2f999a363ad57815201117cca3` | 48985 |

Public release evidence:

- release: `https://github.com/foggy-projects/foggy-runtime-cli/releases/tag/v0.1.6`
- published_at: `2026-06-25T08:39:02Z`
- source_commit: `894188c`
- workflow_run: `28157777945`
- downloaded manifest: `D:/foggy-projects/foggy-data-mcp/.codex-tmp/runtime-cli-public-release-v0.1.6/release-manifest.json`

## Local Build Artifact Evidence

| Artifact | SHA256 | Bytes |
|---|---|---:|
| `foggy_runtime_cli-0.1.6.tar.gz` | `59aa9c9891f1d5b9c433e02105f8ca3ec8954ce47fc73e78416f1b408d91d907` | 50306 |
| `foggy_runtime_cli-0.1.6-py3-none-any.whl` | `5d0ec9de72c57f5b0c808e2860aa3359eeb518a4280518c82685c8add0ef7ebb` | 27421 |

Source evidence:

- `D:/foggy-projects/foggy-data-mcp/foggy-runtime-cli/dist/release-manifest.json`
- `D:/foggy-projects/foggy-data-mcp/foggy-runtime-cli/dist/SHA256SUMS`

## Live Verification Evidence

Evidence directory:

- `D:/foggy-projects/foggy-data-mcp/.codex-tmp/runtime-cli-auth-code-live/20260625-163456/summary.json`

| Check | Result |
|---|---|
| `auth-capabilities` | passed; Runtime API reported `securityMode=auth-code` |
| `auth-bundles-add-no-code` | passed; protected management operation rejected with `RUNTIME_AUTH_REQUIRED` |
| `auth-bundles-add-option-code` | passed; `--auth-code` allowed protected management operation |
| `auth-bundles-add-env-code` | passed; `FOGGY_RUNTIME_API_AUTH_CODE` allowed protected management operation |
| `default-capabilities` | passed; Runtime API reported `securityMode=none-dev-test-only` |
| `default-bundles-add-no-code` | passed; default runtime accepted management operation without auth code |

## Acceptance Criteria

- [x] CLI release target is not lower than the current public latest version.
- [x] Release build passes the full CLI test suite.
- [x] Release artifacts have stable checksum evidence.
- [x] Live Java Runtime API auth-code mode rejects protected management operations without code.
- [x] Live Java Runtime API auth-code mode accepts `--auth-code`.
- [x] Live Java Runtime API auth-code mode accepts `FOGGY_RUNTIME_API_AUTH_CODE`.
- [x] Default `none-dev-test-only` mode remains compatible.
- [x] Public `v0.1.6` release is created and verified.

## Customer Impact

- Customers using CLI against `none-dev-test-only` runtimes are not impacted.
- Customers using CLI against `auth-code` runtimes need CLI `0.1.6` or later for protected management operations.
- Customers can pass the shared code with `--auth-code`, but automation should prefer `FOGGY_RUNTIME_API_AUTH_CODE`.
- This release does not change Runtime API protected endpoint scope.

## Non-Goals

- No server-side Runtime API auth-code contract change.
- No RBAC, per-user permission, audit logging, credential rotation, or security layering.
- No expansion of auth-code protection into query/read/script execution endpoints.
