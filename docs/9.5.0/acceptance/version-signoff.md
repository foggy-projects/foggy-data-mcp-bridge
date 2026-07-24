---
acceptance_scope: version
version: 9.5.0
target: bbd8601df80e1734927eeac7351fe295cb75d74f
doc_role: acceptance-record
status: signed-off
decision: accepted-with-risks
signed_off_by: codex-reviewer
signed_off_at: 2026-07-24
reviewed_by: codex-same-implementation-session
independent_review: false
blocking_items: []
follow_up_required: yes-before-tag-release-publish
evidence_count: 17
assurance_level: elevated
---

# 9.5.0 Version and Release Authority Signoff

## Document Purpose

- intended_for: release-owner / reviewer / project-root-session
- purpose: 对 9.5.0 legacy exit、SPI v2 边界以及新增 canonical release
  authority 的实现与证据形成正式结论。

## Acceptance Basis

- version implementation:
  `docs/9.5.0/workitems/FEATURE-v950-legacy-exit.md`
- authority modernization:
  `docs/9.5.0/workitems/FEATURE-v950-release-authority-modernization.md`
- authority candidate:
  `bbd8601df80e1734927eeac7351fe295cb75d74f`
- current `origin/main`:
  `212fea674a2f0a37eebe7435abbc7cf92f2417c4`
- branch: `codex/v950-release-authority-modernization`
- frozen contract SHA-256:
  `282d06d79456406224d7810400c11c030f31f10e7d17a2fb0848486f926cd9e0`
- final manifest:
  `target/v950-release-authority/runs/v950-release-authority-20260724-r3/final-manifest.json`
- environment: independent Git worktree, JDK 17, Maven, Docker
- original dirty workspace: read-only and unchanged

## Version Goal Conformance

| Goal / Criterion | Delivered | Evidence | Result |
|---|---|---|---|
| 9.5.0 legacy exit | old aggregate/package/bridge/bypass zero guards and SPI v2 roles retained from accepted mainline | existing version signoff evidence plus unchanged product tree | pass |
| current reactor authority | frozen 31-module / 32-project contract and launcher boundary | contract validation and root receipt | pass-with-reuse |
| semantic and database compatibility | semantic 63; seven database variants / 370 tests | exact JUnit receipts, F0/E0/S0 | pass |
| provider fixture integrity | four external database fixture seals unchanged and cleanup passed | cell receipts | pass |
| portable source artifact | deterministic archive, candidate marker and `/dev/shm` replay | archive/extraction receipts and portable 63 | pass |
| evidence integrity | before/after source seal, sensitive scan and 17-receipt final manifest | final manifest and independent hash recomputation | pass-with-process-risk |
| protected boundaries | no `scripts/v934/**`, `.github/**` or protected `docs/9.3.5/**` change | Git diff and dirty-workspace checks | pass |

## Authority Execution Record

The successful evidence chain is intentionally recorded as recovered rather
than represented as one uninterrupted runner invocation:

1. `r1`, candidate `c9286d7a`, completed root `clean verify` with 32/32
   projects, then stopped on root receipt regex and launcher-path defects.
2. `r2`, candidate `6a3c3bb3`, produced passed root, semantic 63 and SQLite
   database 50 receipts, then stopped before MySQL 5.7 because the sealed v934
   provisioner requires its historical cell-root layout.
3. `r3`, candidate `bbd8601d`, is the direct child of `r2`; its committed delta
   is exactly three `scripts/v950/**` files and does not change the frozen
   contract. Fail-closed reuse wrappers bound the three passed source receipts
   to the final candidate. All remaining database lanes, archive creation,
   extraction and portable replay ran fresh on `bbd8601d`.
4. `r3` initially stopped at sensitive scan because a redundant copied r2 root
   Maven log contained a known test-fixture credential. The log was moved to a
   repository-external quarantine; the bound root receipt remained. The scan
   and finalization were rerun without re-running or relabelling product tests.

No failed run is described as passed. The final `r3` status explicitly records
the evidence-packaging recovery.

## Evidence Coverage Summary

| Area | Classification | Required | Evidence | Reused / New | Gaps | Result |
|---|---|---|---|---|---|---|
| root reactor and launcher | core-blocker | 32 projects and JAR boundary | root source receipt + strict wrapper | direct-parent reuse | not fresh on final script-only delta | pass-with-risk |
| semantic replay | core-blocker | exact 63, F0/E0/S0 | semantic source receipt + wrapper | direct-parent reuse | none affecting product tree | pass-with-risk |
| database matrix | core-blocker | 7 variants / 370, F0/E0/S0 | exact lane receipts | SQLite reused; 6 lanes fresh | none | pass |
| database lifecycle | core-blocker | fixture seal and cleanup | four cell receipts | new | none | pass |
| archive portability | core-blocker | exact candidate and cross-filesystem replay | 4431-file archive + portable 63 | new | none | pass |
| source/evidence integrity | core-blocker | stable source seal and complete manifest | seals, scan, 17 receipt hashes | new | recovered scan packaging | pass-with-risk |
| organizational review | process-gap | no false independence claim | same-session evidence review | new | not organizationally independent | risk |

## Executed Evidence

- focused authority self-tests: 8/8 passed.
- contract validation: 31 modules, 32 projects, semantic 63,
  database 7 variants / 370 tests.
- root source receipt: 32/32 projects and launcher JAR boundary passed.
- semantic source receipt: 63 tests, 0 failures/errors/skips.
- database:
  - SQLite standard: 50;
  - MySQL 5.7 standard: 50;
  - MySQL 8 standard + targeted: 50 + 55;
  - PostgreSQL 15 standard + targeted: 50 + 65;
  - SQL Server 2022 standard: 50;
  - total: 7 variants / 370 tests, 0 failures/errors/skips.
- external cells: identical fixture SHA before/after and cleanup passed for
  MySQL 5.7, MySQL 8, PostgreSQL 15 and SQL Server 2022.
- deterministic archive: 4431 tracked files, 131030501 bytes, SHA-256
  `2224675a2af18cf626e871a2b2ca376861f012deed890807c25f2e7780283883`.
- extraction: candidate marker matched and `cross_filesystem=true`.
- portable archive replay: 63 tests, 0 failures/errors/skips.
- source seal: 4431 files, before/after inventory SHA-256
  `3fc5e0d41276ac4af10fcfa70badaafb7b33d5ddc37e98159305a88a4d6c1e45`.
- sensitive scan: 129 evidence files, three patterns, passed; the bound archive
  is excluded because it intentionally contains public test fixtures.
- reviewer recomputed all 17 manifest receipt hashes and the archive hash;
  all matched.
- no run-owned database container remained.
- no command used `mvn install`; GitHub CI, tag, release, publish and remote
  push were not performed.

## Review Findings

- The changed implementation surface is limited to new `scripts/v950/**` and
  its canonical governance record. No product/API/SPI/data behavior changed.
- The active root reactor, database totals, selected report identities,
  provisioner digest, source archive identity and cleanup state are frozen and
  fail closed at receipt creation.
- Missing, extra, stale, failed or skipped JUnit evidence is rejected.
- Archive traversal, candidate tamper, unsafe extraction and incomplete final
  receipt sets have focused negative tests.
- The resume path currently copies a redundant historical root Maven log into
  the evidence tree. When that log contains the known fixture credential, the
  sensitive scan correctly fails and manual evidence quarantine is required.
- The finalizer validates receipt keys, kinds, candidates, status and hashes,
  but does not independently repeat every lane/total/cross-receipt semantic
  check for non-reused receipts. The reviewer therefore recomputed and checked
  the actual receipt set; this is a hardening gap in the one-command authority,
  not evidence of a product failure.
- Review was performed in the implementation session and is not
  organizationally independent.

## Evidence Sufficiency

- assurance_level: elevated
- sufficient_for_scope: yes; product correctness, current artifact identity,
  database compatibility, portable archive replay, source stability and
  cleanup have concrete passed evidence.
- not_sufficient_for: claiming a clean one-command release pointer without
  recovery, organizationally independent approval, or completed
  tag/release/publish operations.
- new_validation_that_could_change_decision: none for product behavior.
  Tool-only hardening would require focused authority-tool tests and an
  evidence re-finalization policy, not another full product matrix by default.

## Waivers

| Waived Item | Authority | Reason | Bounded Impact | Non-Waivable Guards | Follow-up |
|---|---|---|---|---|---|
| recovered authority chain rather than one uninterrupted runner success | repository owner via approved 1–5 execution scope | all interruptions were authority-tool/evidence-packaging defects and passed product receipts remained verifiable | release-governance ergonomics and reproducibility only | exact candidate/source binding, F0/E0/S0, archive identity, fixture seals, cleanup and source seal remain mandatory | harden resume packaging before treating it as a clean one-command pointer |
| same-session review | repository owner via explicit non-independence boundary | no organizational reviewer was requested | process independence only | evidence-first review and truthful disclosure | optional independent release-owner review before publish |

## Blocking Items

- none for preserving the accepted 9.5.0 product result.

## Risks / Follow-ups

- Before tag/release/publish, the release owner should either:
  - accept the recovered-chain limitation explicitly; or
  - remove redundant reused logs from canonical evidence packaging and
    strengthen finalizer lane/total/cross-receipt validation.
- If the authority tool is changed, do not automatically rerun the complete
  product matrix. Reuse remains acceptable only when the candidate delta,
  frozen contract and source receipts are explicitly rebound and verified.
- An organizationally independent reviewer remains recommended for external
  publication, but its absence does not invalidate the product evidence.

## Final Decision

- decision: `accepted-with-risks`
- release readiness label: `READY_WITH_RISKS`
- rationale: all product, artifact, portability, database and source-integrity
  results required by the elevated scope are present and independently
  rechecked. The remaining gaps are bounded to recovery ergonomics,
  finalizer defense-in-depth and review independence.
- blocking_items: none
- follow_up_owner_and_due: release owner, before tag/release/publish

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: codex-reviewer
- signed_off_at: 2026-07-24
- acceptance_record: `docs/9.5.0/acceptance/version-signoff.md`
- blocking_items: none
- follow_up_required: yes-before-tag-release-publish
