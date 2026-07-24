---
doc_type: delivery-spec
delivery_type: release-governance
version: 9.5.0
ticket: v950-release-authority-modernization
status: ULTRA_EXECUTING
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: repository-owner-via-user-request
approved_at: 2026-07-24
open_questions: []
---

# Delivery Spec: 9.5.0 Release Authority Modernization

## Goal

Replace the 9.3.4-specific release collector gap with a 9.5.0 canonical,
fail-closed authority that binds one clean candidate SHA to:

- the current 31-module / 32-project Maven reactor;
- a fresh root `clean verify`;
- the selected SQLite semantic replay;
- the seven-variant SQLite/MySQL/PostgreSQL/SQL Server compatibility replay;
- a deterministic candidate source archive extracted and replayed on a
  different filesystem;
- exact before/after tracked-source seals and one final manifest.

## Scope

- Add only new authority implementation under `scripts/v950/**`.
- Reuse the sealed `scripts/v934/step3/provision-database-cell.sh` solely as
  the external database lifecycle/fixture adapter, pinned by SHA-256.
- Add focused tooling self-tests and this governance record.
- Execute at most one canonical authority attempt after the tooling candidate
  is committed and clean.
- Perform an independent-style review and update the 9.5.0 signoff record.

## Non-Goals

- No changes to `scripts/v934/**`.
- No product/API/SPI/query/security/data behavior changes.
- No `mvn install`, GitHub CI change, tag, release, publish, or remote push.
- No claim of organizational independence; review independence is by
  evidence-first role separation in the current session.

## Frozen Contract

- Contract: `scripts/v950/release-authority-contract.json`.
- Runner: `scripts/v950/verify-release-authority.sh`.
- Evidence tool: `scripts/v950/release_authority_tool.py`.
- Candidate must be a clean committed HEAD with current `origin/main` as an
  ancestor.
- All declared reports, test counts, database cleanup receipts, archive
  identity, cross-filesystem property, and source seals are non-waivable.
- Missing/extra/stale/failed/skipped evidence fails closed.
- The two historically tracked root `target/**` manifests may be temporarily
  displaced by Maven `clean`; the runner must restore their exact bytes on
  every exit path before the after-seal and clean-state checks.

## Acceptance Criteria

- [ ] AC-1: contract validation proves the exact active reactor and rejects
  legacy aggregate/package identities in the v950 authority contract.
- [ ] AC-2: focused self-tests cover contract drift, JUnit report mismatch,
  unsafe archive paths, archive tamper, and final-manifest incompleteness.
- [ ] AC-3: one clean candidate completes root verify, semantic replay,
  seven database variants, cross-filesystem archive replay, source seal, and
  sensitive-evidence scan.
- [ ] AC-4: final manifest binds all receipts to one candidate SHA and reports
  zero failures/errors/skips.
- [ ] AC-5: original dirty workspace remains exact and untouched.
- [ ] AC-6: final review updates the canonical signoff without tagging,
  publishing, or claiming organizational independence.

## Validation Budget

- Focused tool validation: Python unit tests, contract validation, `bash -n`.
- Canonical authority: one attempt, expected 30–90 minutes depending on Docker
  image availability and Maven cache state.
- No second expensive attempt without a newly approved replan if the first
  attempt exposes a product defect or a non-trivial contract defect.

## Implementation Result

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- deviations: pending
- residual_risks: pending
- readiness: pending

## Acceptance Status

- acceptance_status: pending
- acceptance_decision: pending
- signed_off_by: pending
- signed_off_at: pending
- acceptance_record: `docs/9.5.0/acceptance/version-signoff.md`

