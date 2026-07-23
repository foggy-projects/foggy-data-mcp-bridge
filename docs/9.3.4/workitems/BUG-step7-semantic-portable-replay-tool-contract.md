---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: v934-step7-semantic-portable-replay
status: ACCEPTED
canonical: true
execution_mode: ultra
approved_by: repository-owner-via-user-request
approved_at: 2026-07-23
accepted_by: repository-owner-via-user-approval
accepted_at: 2026-07-23
acceptance_record: docs/9.3.4/acceptance/BUG-step7-semantic-portable-replay-tool-contract-signoff-20260723.md
open_questions: []
---

# Delivery Spec: Step 7 Semantic Portable Replay Tool Contract

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: freeze the minimum repair required to make a byte-verified v9.3.4
  release artifact semantically replayable without weakening any verifier.
- canonical_path:
  `docs/9.3.4/workitems/BUG-step7-semantic-portable-replay-tool-contract.md`
- parent_feature:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`

## Goal

- version_goal: complete Step 7 with portable, independently recomputable local
  authority evidence.
- target_outcome: semantic replay materializes canonical Step 4 evidence and
  tested production classes as independent copies, restores only the complete
  audited verifier-required canonical permissions, records the transformation,
  and passes deterministic positive and negative regression coverage.

## Scope

- in_scope:
  - audit `scripts/v934/step5/portable_replay_tool.py`,
    `scripts/v934/step5/release_artifact_tool.py`,
    `scripts/v934/step4/coverage_xml_tool.py`, their manifests, dispatchers and
    existing tests/negative tools;
  - replace canonical evidence and tested-class hardlink materialization with
    independent copy semantics that guarantee `st_nlink == 1`;
  - define one explicit, minimal, auditable semantic-replay permission policy
    for every canonical path whose verifier mode differs from the normalized
    extracted artifact mode, including at least `child-ready/*.json -> 0600`;
  - preserve the extracted artifact tree byte-for-byte and metadata-for-metadata
    by applying canonical transformations only in a separate replay target;
  - extend the replay receipt with copy counts, permission-restoration items and
    the exact named/versioned policy;
  - add deterministic positive and negative regressions for same-filesystem and
    cross-filesystem behavior, link count, modes, receipt content, source
    immutability and fail-closed drift.
- affected_modules: `scripts/v934/step5`, governed Step 4 coverage replay
  contracts/tests, Step 5/Step 6 hash closures and `docs/9.3.4`.
- external_dependencies: local POSIX filesystem semantics; an independently
  selected cross-filesystem location when available for the regression proof.

## Non-Goals

- out_of_scope:
  - production Java API/SPI, POM behavior, database/test semantics, coverage
    thresholds/exclusions or verifier safety checks;
  - GitHub Actions/Step 6 enablement, required checks, branch protection, tag,
    release or publish;
  - rewriting or promoting the old Step 7 candidate as final authority.
- do_not_touch:
  - the user's original dirty workspace and its `docs/9.3.5` changes;
  - the extracted artifact contents or metadata after extraction;
  - historical failed/candidate evidence and pointers;
  - final authority pointer publication before semantic replay and the ordered
    independent review chain pass.

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| Canonical evidence and tested classes always use independent copy, never hardlink | Coverage identity checks require single-link inodes; filesystem-dependent fallback is not deterministic | Every materialized regular file must finish with `st_nlink == 1` |
| Restore verifier-required modes only in a separate canonical replay target | Release artifacts intentionally normalize portable ordinary files, while verifier-private evidence has stricter runtime modes | Extracted artifact bytes and metadata remain unchanged |
| Audit the full special-mode set before implementation is considered complete | Fixing only the observed `child-ready/*.json` case could leave another cross-filesystem failure | Policy is explicit, path-scoped, minimal and covered by tests |
| Receipt records transformation counts and policy items | Replay must be independently auditable | Schema drift or unrecorded transformation fails closed |
| Coverage verifier checks remain unchanged | The defect is producer/replay contract mismatch, not verifier overreach | No relaxed mode, owner, regular-file, size or link-count checks |
| Tool source change invalidates the old source seal | Step 5/Step 7 authority binds governed tool bytes | One replacement Step 5 rehearsal and one new Step 7 authority are mandatory |

## Acceptance Criteria

- [x] AC-1: complete audit enumerates every coverage semantic-replay path with a
  verifier-required mode different from normalized artifact mode; the policy
  contains no wildcard broader than the verifier contract it represents.
- [x] AC-2: all canonical evidence and tested class files are independently
  copied on same-filesystem and cross-filesystem replay and finish regular,
  non-symlink and `st_nlink == 1`.
- [x] AC-3: canonical permission restoration makes all governed verifier mode
  checks pass, including `child-ready/*.json == 0600`, without changing the
  extracted artifact tree's bytes, modes, ownership, link counts or timestamps.
- [x] AC-4: the receipt deterministically records total copied files, copied
  canonical evidence, copied tested classes, every permission-restoration
  policy item and applied count; repeated equivalent replay yields equivalent
  semantic receipt content apart from explicitly allowed target paths.
- [x] AC-5: focused positives and negative mutations cover hardlink attempts,
  missing/extra permission rules, wrong restored mode, source/extracted-tree
  mutation, receipt drift and same-/cross-filesystem execution; all negatives
  fail closed.
- [x] AC-6: Step 4/5/6 governed hash manifests and static contract tests are
  updated consistently; existing release artifact, coverage XML and pointer
  safety negatives remain green.
- [x] AC-7: implementation and evidence documentation are updated, exact test
  commands/results are recorded, deviations are none or trigger
  `NEEDS_REPLAN`, and this BUG reaches `READY_FOR_SIGNOFF` without self-signing
  `ACCEPTED`.

## Contract / Data / Security Constraints

- API or event contract: no production API/SPI changes. Replay receipt changes
  are limited to the governed local evidence schema and must remain fail-closed.
- data and migration: N/A; no database or user-data migration.
- compatibility and rollback: rollback is removal of the repair commit before a
  replacement authority begins. The old Step 7 run remains historical
  candidate evidence and cannot become fallback authority.
- permissions and secrets: do not broaden permissions. Private verifier inputs
  retain their exact stricter modes; sensitive scan and ownership checks remain
  mandatory.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1/AC-3 | critical | static audit plus real coverage verifier replay | enumerated mode policy and verifier outputs |
| AC-2/AC-4 | critical | deterministic same-FS and cross-FS materialization tests | stat identities and replay receipts |
| AC-5 | critical | focused positive plus mutation/negative suite | exact command, case counts and failure codes |
| AC-6 | major | affected Step 4/5/6 contract/hash/static suites | updated digests and passing summaries |
| AC-7 | major | implementation self-review then independent quality chain | work item result and review records |

Validation order is `focused -> affected contract/negative lanes -> one final
semantic replay`. Focused/static work is `<5m`; affected lanes are expected
`5-30m`. Replacement Step 5 and Step 7 are each `>60m` and are not implementation
tests for this BUG.

## Bug Context

- bug_source: acceptance-found
- severity: critical
- environment: v9.3.4 Step 7 authority artifact from tested commit
  `62bf3fe1456af01179f09e2a9a80c3d229e2435f`, run
  `step7-local-authority-20260723-r1`.
- current_behavior: same-filesystem replay fails `E_RUN_CONTEXT` because
  canonical evidence is hardlinked; cross-filesystem replay fails
  `E_CHILD_LIFECYCLE` because normalized `0644` child-ready JSON does not meet
  canonical `0600` verifier requirements.
- expected_behavior: replay is filesystem-independent, creates independent
  canonical inodes, restores the complete exact canonical mode policy, keeps the
  extracted source untouched and passes unchanged verifiers.
- reproduction_steps: verify the old authority archive, materialize semantic
  replay once on the same filesystem and once on a distinct filesystem, then run
  the governed coverage verifier against the canonical target.
- reproduction_status: confirmed
- existing_evidence: old authority candidate pointer exists; final pointer is
  absent; downstream errors are `E_RUN_CONTEXT` and `E_CHILD_LIFECYCLE`.
- existing_tests: Step 5 artifact/package/pointer negatives and Step 4 coverage
  contract/XML negatives exist but do not cover the producer/verifier mode and
  link-count intersection.
- regression_protection: required
- waiver_reason_and_risk: N/A

## Validation Cost and Attempt Control

- repair validation attempts are not authority attempts and may be rerun only
  when implementation/test inputs change.
- replacement Step 5 rehearsal: maximum attempts=`1`; expected `>60m`; start only
  after this BUG is `READY_FOR_SIGNOFF`, review chain passes, repair bytes are
  clean/pushed and the frozen contract/attempt ledger is rechecked.
- replacement Step 7 authority: maximum attempts=`1`; expected `>60m`; start only
  after replacement Step 5 is independently accepted and the exact clean
  `origin/main` entry plus explicit activation is frozen.
- reusable evidence: old Step 7 runner output is historical diagnostic input
  only; unchanged product test semantics do not authorize splicing raw lane
  output into either replacement run.
- stop/replan: any need to change production API/SPI, coverage verifier safety,
  release artifact global normalization semantics beyond the canonical replay
  boundary, or any scope beyond portable replay/release tooling sets
  `NEEDS_REPLAN` and stops implementation. Two non-product failures of an
  expensive run also require a new replan before another run.

## Risks and Open Questions

- known_risks: special permissions may exist beyond the two observed failure
  points; the mandatory full audit and exact-policy regression are the control.
- open_questions: none

## Ultra Execution Contract

- Read this file, project rules, the parent feature and all three named tools
  before editing.
- Establish deterministic regression failures where practical before the fix.
- Keep implementation local to portable replay/release tooling and governed
  tests/manifests; stop on production API/SPI or broader release semantic scope.
- Record exact commands, case counts, changed paths, policy inventory and any
  deviations in `Implementation Result`.
- On completion set this BUG to `READY_FOR_SIGNOFF`; never self-set `ACCEPTED`.

## Implementation Result

- implementation_summary: `portable_replay_tool.py` now materializes canonical
  Step 4 evidence and tested production classes exclusively through independent
  exclusive-create copies. It rejects multi-link inputs, verifies distinct
  source/target inode identity and `st_nlink == 1`, restores one exact named
  permission policy in the separate canonical target, and proves the extracted
  tree's complete entry metadata and digest snapshot did not change. The
  receipt records policy/version, per-tree and total copy counts, zero
  hardlinks, and every applied permission restoration.
- audited_permission_inventory:
  - release artifact normalization remains unchanged: directories=`0755`,
    ordinary files=`0644`, governed executable files=`0755`;
  - coverage canonical exact modes are `run-context.json=0644`,
    `source-before.tsv=0644`, `source-after.tsv=0644`,
    `child-lifecycle/{unit,integration,step3-required}-complete.env=0644`, and
    the effective reporter receipt=`0644`;
  - the complete normalization conflict is exactly
    `child-ready/{unit,integration,step3-required}.json: 0644 -> 0600`;
    there is no wildcard and no additional database/unit/package verifier mode
    exception.
- changed_paths:
  - `scripts/v934/step5/portable_replay_tool.py`;
  - `scripts/verify-v934-release-gate.sh`;
  - governed Step 4/5/6 `SHA256SUMS`, Step 6 `ci-contract.json` and
    `ci_contract_tool.py` hash closure;
  - this BUG, its parent feature and `docs/9.3.4/README.md`.
- tests_and_results:
  - `python3 scripts/v934/step5/portable_replay_tool.py self-test
    --cross-filesystem-root /dev/shm` and the no-argument auto-selection form:
    same-filesystem evidence plus tested-classes checks passed, distinct-device
    replay passed, and `9/9` mutation negatives failed closed;
  - `python3 scripts/v934/step5/release_artifact_tool.py self-test`:
    `105` negatives and `2` deterministic rebuilds passed;
  - `python3 scripts/v934/step4/coverage_xml_negative_tool.py --repo-root .
    --output <fresh-output>`: `130/130` passed;
  - `python3 scripts/v934/step4/coverage_contract_negative_tool.py --repo-root .
    --output <fresh-output>`: `28/28` passed;
  - `python3 scripts/v934/step5/release_package_tool.py negative --repo-root .
    --output-dir <fresh-output>`: `120/120` passed;
  - `python3 scripts/v934/step5/pointer_tool.py negative --output-dir
    <fresh-output>`: `5/5` passed; `final-negative` separately passed `66/66`;
  - `python3 scripts/v934/step6/ci_contract_tool.py negative --repo-root .
    --output-dir <fresh-output>`: `86/86` passed;
  - Step 4/5/6 `sha256sum -c` and
    `ci_contract_tool.py validate-workflows --repo-root .` passed with
    `16` governed tooling paths and `4` unchanged workflows.
- manual_or_experience_evidence: same-device replay creates different inodes
  rather than hardlinks; cross-device replay produces the same semantic policy
  receipt; all three private child-ready targets are `0600`; source entry
  identity, ownership, link count, mode, size, mtime and SHA-256 remain exact.
  Focused evidence was written under
  `/tmp/v934-step7-replan-focused-20260724`.
- deviations: none
- residual_risks: the repair changes governed tool bytes and therefore cannot
  reuse the old source seal. One replacement Step 5 rehearsal and one new Step
  7 authority remain mandatory after independent signoff and exact clean/pushed
  entry revalidation. The old candidate remains non-final.
- readiness: READY_FOR_SIGNOFF; no `ACCEPTED` decision is asserted here.

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: repository-owner-via-user-approval
- signed_off_at: 2026-07-23
- acceptance_record:
  `docs/9.3.4/acceptance/BUG-step7-semantic-portable-replay-tool-contract-signoff-20260723.md`
- blocking_items: none
- follow_up_required: yes；replacement Step 5 and new Step 7 remain separately
  governed authority attempts.

## References

- parent feature:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- old authority evidence:
  `/tmp/foggy-v934-step7-authority-20260723.LwJ1DR/repo/target/v934-release-gate/runs/step7-local-authority-20260723-r1`
