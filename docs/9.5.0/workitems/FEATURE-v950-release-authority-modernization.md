---
doc_type: delivery-spec
delivery_type: release-governance
version: 9.5.0
ticket: v950-release-authority-modernization
status: ACCEPTED
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

- [x] AC-1: contract validation proves the exact active reactor and rejects
  legacy aggregate/package identities in the v950 authority contract.
- [x] AC-2: focused self-tests cover contract drift, JUnit report mismatch,
  unsafe archive paths, archive tamper, final-manifest incompleteness,
  governance-only ancestor reuse, swapped lane, tampered totals and
  archive/extraction mismatch.
- [x] AC-3: one clean candidate completes root verify, semantic replay,
  seven database variants, cross-filesystem archive replay, source seal, and
  sensitive-evidence scan. Passed product receipts were rebound from ancestor
  candidates under the documented governance-only rule; archive, extraction,
  portable replay, source seals, sensitive scan and final manifest ran fresh
  on the final hardening candidate.
- [x] AC-4: final manifest binds all receipts to one candidate SHA and reports
  zero failures/errors/skips.
- [x] AC-5: original dirty workspace remains exact and untouched.
- [x] AC-6: final review updates the canonical signoff without tagging,
  publishing, or claiming organizational independence.

## Validation Budget

- Focused tool validation: Python unit tests, contract validation, `bash -n`.
- Canonical authority: one attempt, expected 30–90 minutes depending on Docker
  image availability and Maven cache state.
- No second expensive attempt without a newly approved replan if the first
  attempt exposes a product defect or a non-trivial contract defect.

## Implementation Result

- implementation_summary: added a 9.5.0-native frozen authority contract,
  evidence utility, fail-closed runner, focused self-tests, current-reactor
  root/JAR validation, exact JUnit receipts, seven-variant database lifecycle,
  deterministic source archive, cross-filesystem replay, source seal,
  sensitive scan and final manifest. Follow-up hardening removed raw reused
  logs/reports from recovery packaging and made finalization repeat exact
  lane/database/totals plus archive/source cross-receipt validation.
- changed_paths:
  `scripts/v950/release-authority-contract.json`,
  `scripts/v950/release_authority_tool.py`,
  `scripts/v950/tests/test_release_authority_tool.py`,
  `scripts/v950/verify-release-authority.sh`, this canonical record, and
  `docs/9.5.0/workitems/BUG-v950-release-authority-evidence-hardening.md`.
- tests_and_results:
  - final focused tool validation: 14/14 passed; contract reports 31 modules,
    32 projects, semantic 63 and database 7 variants / 370 tests;
  - root clean verify source receipt: 32/32 projects and launcher JAR boundary
    passed on direct-parent candidate `6a3c3bb3dd0d650ee0a514187f802d2e66ee9c60`;
  - semantic source receipt: 63 tests, F0/E0/S0 on the same direct parent;
  - database matrix: SQLite source receipt 50 plus fresh MySQL 5.7 50,
    MySQL 8 50+55, PostgreSQL 15 50+65 and SQL Server 2022 50;
    total 7 variants / 370 tests, F0/E0/S0;
  - all four external cells: fixture before/after SHA equal and cleanup passed;
  - hardening candidate archive: 4432 tracked files, 131034459 bytes,
    SHA-256
    `45bd6d33d7f2c5b17295c8cf9a28bc14bb4e1060bcc738fabe99c941e23cc945`;
  - cross-filesystem portable replay on the hardening candidate:
    63 tests, F0/E0/S0;
  - final manifest:
    `target/v950-release-authority/runs/v950-release-authority-hardening-20260724-r1/final-manifest.json`,
    candidate `03af64da7330d70d3364c9e92992710a2ed3a111`, status passed.
- deviations:
  - r1 and r2 stopped on authority-tool defects after preserving their actual
    passed evidence; neither is represented as a passed authority run;
  - r3 used fail-closed direct-parent evidence reuse because the candidate
    delta was exactly three `scripts/v950/**` files and did not change the
    frozen contract;
  - r3 initially stopped at sensitive scan because a redundant copied root
    Maven log contained a known test-fixture credential. The log was moved to
    a repository-external quarantine, the bound source receipt remained, and
    sensitive scan plus finalization were rerun without product-test reruns.
  - the follow-up BUG removed that packaging defect, strengthened finalizer
    semantics and completed one uninterrupted partial authority in 1 minute
    31 seconds without Docker or product-matrix execution.
- residual_risks:
  - review is evidence-first in the same Codex session and does not claim
    organizational independence; this is an explicitly excluded process
    property, not a technical authority gap.
- readiness: READY_FOR_TAG

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex-reviewer
- signed_off_at: 2026-07-24
- acceptance_record: `docs/9.5.0/acceptance/version-signoff.md`
- blocking_items: none
- follow_up_required: no
