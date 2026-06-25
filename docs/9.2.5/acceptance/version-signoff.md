---
acceptance_scope: version
version: 9.2.5
target: Foggy Runtime API Auth-Code CLI Release and Boundary Evaluation
doc_role: acceptance-record
doc_purpose: Record formal version acceptance and signoff decision for 9.2.5.
status: signed-off
decision: accepted
signed_off_by: codex
signed_off_at: 2026-06-25
reviewed_by: codex
blocking_items: []
follow_up_required: no
evidence_count: 8
---

# Version Acceptance

## Document Purpose

- doc_type: acceptance
- intended_for: signoff-owner / reviewer / root-controller
- purpose: Record the formal 9.2.5 acceptance decision and evidence summary.

## Background

- Version: 9.2.5
- Scope: Runtime CLI `0.1.6` public release closure and Runtime API auth-code technical boundary evaluation.
- Goal: Close the customer-facing CLI delivery gap for `securityMode=auth-code` runtimes while preserving the agreed shared-code-only technical boundary.
- Boundary: No Runtime API server auth-code contract change, RBAC, audit, per-user permission model, credential rotation, protected-operation scope expansion, or security layering change.

## Acceptance Basis

- `docs/9.2.5/README.md`
- `docs/9.2.5/workitems/P1-runtime-cli-auth-code-release-closure.md`
- `docs/9.2.5/workitems/P1-runtime-api-auth-code-security-boundary-evaluation.md`
- `docs/9.2.5/quality/P1-runtime-cli-auth-code-release-closure-implementation-quality.md`
- `docs/9.2.5/coverage/P1-runtime-cli-auth-code-release-closure-coverage-audit.md`
- `docs/9.2.1/workitems/P1-runtime-api-auth-code-technical-closure.md`

## Module Summary

| Module | Owner | Status | Acceptance Record | Notes |
|---|---|---|---|---|
| `foggy-runtime-cli` | codex | signed-off | `docs/9.2.5/acceptance/version-signoff.md` | Public CLI `v0.1.6` release, auth-code option/env/header path, release metadata, and install smoke accepted. |
| `foggy-runtime-api` | codex | signed-off | `docs/9.2.5/acceptance/version-signoff.md` | Existing auth-code management gate boundary accepted without server contract changes. |
| `foggy-fsscript` | codex | signed-off | `docs/9.2.5/acceptance/version-signoff.md` | Legacy bundle management boundary remains default-off; runtime-api protected path remains the accepted integration path. |

## Checklist

- [x] Scope workitems have complete status through workitem, quality gate, and coverage audit records.
- [x] Runtime CLI `0.1.6` is the correct next public release after `v0.1.5`.
- [x] Public release assets are published and verifiable from GitHub Release.
- [x] Local release build test evidence is complete and traceable.
- [x] Live Java Runtime API auth-code verification covers no-code rejection, `--auth-code`, env auth-code, and default no-auth compatibility.
- [x] Experience verification is `N/A` because this is CLI/API behavior with no UI surface.
- [x] Customer impact is documented for `none-dev-test-only`, configured `auth-code`, and read/query/script execution commands.
- [x] Guardrails were preserved: no security layering, RBAC, user permissions, audit, rotation, or Runtime API auth-code contract change.

## Evidence

- Test:
  - `powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1 -Clean` in `D:/foggy-projects/foggy-data-mcp/foggy-runtime-cli`: passed, 70/70 tests.
  - `D:/foggy-projects/foggy-data-mcp/.codex-tmp/runtime-cli-auth-code-live/20260625-163456/summary.json`: passed, 6/6 live checks.
  - Public wheel install smoke from GitHub Release: passed; `foggy-runtime --help` includes `--auth-code`.
- Experience:
  - N/A. No UI page, form, navigation, browser interaction, or visual workflow was changed.
- Delivery Artifacts:
  - Public release: `https://github.com/foggy-projects/foggy-runtime-cli/releases/tag/v0.1.6`
  - Public CLI repo commit: `894188c`
  - Workspace commit: `4ab1c32`
  - Bridge documentation commit: `27256585`
  - Runtime local-state ignore cleanup commit: `847ae21d`
  - Public wheel SHA256: `bace0b6568ae6a5774282c1ebbbea198bf88b284cdbf03810c771e5aa6f8e3ff`
  - Public sdist SHA256: `11efe70e6110e952eab1a034c8325e70a5490d2f999a363ad57815201117cca3`

## Blocking Items

- none

## Risks / Open Items

- Shared auth code is not a customer-facing permission model. This is accepted by scope and remains documented as an internal technical gate.
- Passing `--auth-code` can expose the code through shell history or process inspection. This is non-blocking because `FOGGY_RUNTIME_API_AUTH_CODE` is documented and verified for automation.
- Query, read, SQL probe, compose, and fsscript execution endpoints remain outside the auth-code management gate by design.
- Standalone FSScript-only deployments that explicitly re-enable legacy `/api/bundles/**` must protect that surface operationally; the accepted protected path is Runtime API integration.

## Final Decision

Decision: `accepted`.

9.2.5 satisfies the documented release-closure and technical-boundary scope. The public CLI `v0.1.6` release is published, customer installation was smoke-tested, local and live Runtime API evidence is traceable, implementation quality and coverage audit records are passed, and no blocking issue remains inside the agreed shared-code management-gate boundary.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-25
- acceptance_record: docs/9.2.5/acceptance/version-signoff.md
- blocking_items: none
- follow_up_required: no
