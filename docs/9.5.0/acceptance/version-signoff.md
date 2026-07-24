---
acceptance_scope: version
version: 9.5.0
target: 03af64da7330d70d3364c9e92992710a2ed3a111
doc_role: acceptance-record
status: signed-off
decision: accepted
signed_off_by: codex-reviewer
signed_off_at: 2026-07-24
reviewed_by: codex-same-implementation-session
independent_review: false
blocking_items: []
follow_up_required: no
evidence_count: 17
assurance_level: elevated
---

# 9.5.0 Version and Release Authority Signoff

## Document Purpose

- intended_for: release-owner / reviewer / project-root-session
- purpose: 对 9.5.0 legacy exit、SPI v2 边界、canonical release authority
  及其 evidence-hardening 形成最终、可复核的版本签收结论。

## Background

- version_goal:
  完成第一方 model legacy exit，并以当前 31-module / 32-project 图形成
  fail-closed、可携带、可复现的 9.5.0 release authority。
- success_criteria:
  已批准 legacy zero guards 与 capability truthfulness 保持通过；产品、数据库、
  archive portability、source identity 和 evidence integrity 均有充分证据。
- canonical delivery specs:
  - `docs/9.5.0/workitems/FEATURE-v950-legacy-exit.md`
  - `docs/9.5.0/workitems/FEATURE-v950-release-authority-modernization.md`
  - `docs/9.5.0/workitems/BUG-v950-release-authority-evidence-hardening.md`
- final authority candidate:
  `03af64da7330d70d3364c9e92992710a2ed3a111`
- candidate parent authority:
  `bbd8601df80e1734927eeac7351fe295cb75d74f`
- current fetched `origin/main`:
  `212fea674a2f0a37eebe7435abbc7cf92f2417c4`
- branch:
  `codex/v950-release-authority-hardening`
- frozen contract SHA-256:
  `282d06d79456406224d7810400c11c030f31f10e7d17a2fb0848486f926cd9e0`
- final manifest:
  `target/v950-release-authority/runs/v950-release-authority-hardening-20260724-r1/final-manifest.json`
- original dirty workspace:
  read-only and unchanged.

## Version Goal Conformance

| Goal / Criterion | Delivered | Evidence | Result |
|---|---|---|---|
| 9.5.0 legacy exit | old aggregate/package/bridge/bypass zero guards and approved SPI v2 roles | accepted legacy-exit workitem and unchanged product tree | pass |
| current reactor authority | frozen 31 modules / 32 projects and launcher boundary | contract validation plus rebound passed root receipt | pass |
| semantic and database behavior | semantic 63; seven variants / 370 tests, F0/E0/S0 | exact source receipts and strict wrappers | pass |
| provider fixture integrity | four external DB fixture seals equal and cleanup passed | strict cell receipts and wrappers | pass |
| portable source artifact | deterministic archive and real `/dev/shm` replay | fresh archive/extraction plus portable 63 | pass |
| source/evidence integrity | exact before/after source seal, sensitive scan and 17 receipts | fresh final manifest and independent hash audit | pass |
| authority hardening | minimal reuse packaging and exact finalizer semantics | 14 focused tests and partial authority | pass |
| protected boundaries | no product, contract, v934, CI or protected 9.3.5 change | candidate diff and workspace audit | pass |

## Module / Workitem Summary

| Delivery | Spec | Status | Notes |
|---|---|---|---|
| model legacy exit | `FEATURE-v950-legacy-exit.md` | ACCEPTED | approved source/binary break, stable model-api QueryFacade retained |
| release authority modernization | `FEATURE-v950-release-authority-modernization.md` | ACCEPTED | current-layout contract, archive, DB matrix and final manifest |
| authority evidence hardening | `BUG-v950-release-authority-evidence-hardening.md` | ACCEPTED | packaging and finalizer gaps closed |

## Authority Execution Record

The original authority evidence was completed on candidate `bbd8601d` and
truthfully recorded its recovery history. The accepted hardening candidate
changes only:

- `scripts/v950/release_authority_tool.py`
- `scripts/v950/verify-release-authority.sh`
- `scripts/v950/tests/test_release_authority_tool.py`
- `docs/9.5.0/workitems/BUG-v950-release-authority-evidence-hardening.md`

The full diff from the oldest reused source candidate
`6a3c3bb3dd0d650ee0a514187f802d2e66ee9c60` to `03af64da` contains exactly
those four governance paths. The frozen contract and all product paths are
unchanged.

One uninterrupted partial authority ran from
`2026-07-24T14:57:08Z` to `14:58:39Z`:

- root, semantic, seven database variants and four database cells were
  rebound from passed source receipts;
- each wrapper recomputed source ancestry, the complete changed-path set,
  source receipt SHA-256 and exact effective receipt semantics;
- no root, semantic or database Maven log/raw-report directory was copied;
- Docker/database provisioning and product Maven matrix execution were not
  started;
- archive, cross-filesystem extraction, portable replay, source seals,
  sensitive scan and final manifest ran fresh on `03af64da`;
- the runner exited 0 with `status=passed`.

## Executed and Reused Evidence

- focused authority tooling:
  - Python compile: passed;
  - runner `bash -n`: passed;
  - Python unittest: 14/14 passed;
  - contract validation: 31 modules, 32 projects, semantic 63,
    database 7 variants / 370 tests.
- reused product evidence:
  - root reactor: 32/32 projects and launcher JAR boundary;
  - semantic: 63 tests, F0/E0/S0;
  - SQLite standard: 50;
  - MySQL 5.7 standard: 50;
  - MySQL 8 standard + targeted: 50 + 55;
  - PostgreSQL 15 standard + targeted: 50 + 65;
  - SQL Server 2022 standard: 50;
  - database total: 7 variants / 370 tests, F0/E0/S0;
  - all four external DB cells: fixture seal unchanged and cleanup passed.
- fresh candidate evidence:
  - deterministic archive: 4432 tracked files, 131034459 bytes;
  - archive SHA-256:
    `45bd6d33d7f2c5b17295c8cf9a28bc14bb4e1060bcc738fabe99c941e23cc945`;
  - extraction: 4433 files, `cross_filesystem=true`, same archive hash;
  - portable replay: 63 tests, F0/E0/S0;
  - source-before/source-after: 4432 files, byte-identical inventory,
    SHA-256
    `263d09fea10157507e98eca02dcf8b225a6243f424f18ffa4d10d35aa57816aa`;
  - sensitive scan: 46 files, 3 patterns, one bound archive excluded, passed;
  - final manifest: 17 receipts, F0/E0/S0, passed.
- reviewer audit:
  - 17/17 receipt SHA-256 values independently recomputed and matched;
  - source archive SHA-256 independently recomputed and matched;
  - before/after source inventory compared byte-for-byte;
  - only fresh portable Maven log and its 11 JUnit XML reports exist in the
    new evidence package.

## Evidence Coverage Summary

| Area | Classification | Required | Evidence | Reused / New | Gaps | Result |
|---|---|---|---|---|---|---|
| legacy/SPI product outcome | core-blocker | accepted 9.5.0 result remains unchanged | prior accepted product evidence plus zero product diff | reused | none | pass |
| root/launcher | core-blocker | 32 projects and artifact boundary | strict root source receipt/wrapper | reused | none | pass |
| semantic/database | core-blocker | 63 plus 7/370, F0/E0/S0 | exact report/cell receipts | reused | none | pass |
| archive portability | core-blocker | exact candidate, cross-FS replay | 4432-file archive, extraction and portable 63 | new | none | pass |
| source/evidence integrity | core-blocker | stable source and complete manifest | seals, scan, 17 hashes | new | none | pass |
| packaging regression | core-blocker | no reused raw logs/reports | runner test and evidence inventory | new | none | pass |
| organizational review | process-gap / non-goal | no false independence claim | explicit disclosure | new | not organizationally independent | pass-for-approved-scope |

## Review Findings

- Changed surface matches the approved BUG exactly; no product, API/SPI,
  contract, data, security, CI or historical sealed script change exists.
- Reuse is ancestor-only and fails when any path leaves the explicit
  governance allowlist or changes the frozen contract.
- Finalizer now validates each effective receipt's exact key/kind,
  lane/database, report identities, totals, contract and candidate binding.
- Archive/extraction/source-seal hash and file-count relationships are checked
  together rather than as isolated receipts.
- Negative tests cover swapped lane, tampered totals, archive mismatch,
  incomplete receipt sets, frozen-contract changes and raw-evidence packaging.
- No debug branch, test bypass, unexplained TODO, secret, compatibility
  weakening or irreversible operation was found.
- Review is evidence-first in the same Codex session. It does not claim
  organizational independence, which was explicitly outside the approved
  authority scope.

## Evidence Sufficiency

- assurance_level: elevated for the version; standard for the tooling BUG.
- why sufficient:
  all non-waivable product, artifact, database, source and evidence-integrity
  outcomes have exact passed evidence. The hardening changed only governance
  tooling, and all directly affected authority phases were regenerated on the
  committed candidate.
- new validation that could change decision:
  none. A fresh root/semantic/database matrix would repeat unchanged product
  evidence and would not improve the tooling-specific conclusion.
- expensive validation omitted:
  no second full product authority, Step 5/7, CI, install, tag, release,
  publish or push. The owner-approved partial authority was the minimum
  decision-changing radius and passed.

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: no invalidated product evidence or remaining
  authority-integrity gap.
- maximum_attempts: 1 already completed for the approved partial authority.
- user_approval: approved
- execution_status: passed

## Waivers

| Waived Item | Authority | Reason | Bounded Impact | Non-Waivable Guards | Follow-up |
|---|---|---|---|---|---|
| none | | | | | |

## Blocking Items

- none

## Process Notes

- 9.5.0 intentionally breaks the removed Maven coordinate, old Java package
  identities and deprecated bridge. The migration/rollback guide remains
  mandatory for external consumers; this is approved release behavior rather
  than an unresolved authority defect.
- Tag, release, publish, push and mainline integration require their own
  explicit repository-owner authorization and were not performed here.
- An organizationally independent release-owner review may still be chosen
  as policy, but it is not a missing technical prerequisite in the approved
  scope.

## Final Decision

- decision: `accepted`
- release readiness label: `READY_FOR_TAG`
- rationale:
  all approved 9.5.0 product outcomes remain valid; the canonical authority
  now produces a clean, minimal, one-command passed evidence pointer with
  exact semantic and cross-receipt validation. The previously accepted
  packaging and finalizer risks are closed.
- blocking_items: none
- follow_up_owner_and_due:
  none for technical signoff; repository owner controls any later
  mainline/tag/release/publish operation.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex-reviewer
- signed_off_at: 2026-07-24
- acceptance_record: `docs/9.5.0/acceptance/version-signoff.md`
- blocking_items: none
- follow_up_required: no
