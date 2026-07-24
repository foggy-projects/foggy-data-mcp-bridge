---
acceptance_scope: bug
version: 9.3.4
target: v934-step7-semantic-portable-replay
status: signed-off
decision: accepted
signed_off_by: repository-owner-via-user-approval
signed_off_at: 2026-07-23
reviewed_by: codex-delivery-signoff-audit
blocking_items: []
follow_up_required: yes
evidence_count: 7
---

# Step 7 semantic portable replay tool contract signoff

## Document Purpose

- intended_for: release owner / project-root session
- purpose: 对 Step 7 downstream semantic replay 暴露的 copy/link-count 与
  canonical mode 契约冲突形成正式、可复核的 BUG 修复签收结论。

## Background

- delivery_spec:
  `docs/9.3.4/workitems/BUG-step7-semantic-portable-replay-tool-contract.md`
- target_outcome: canonical evidence 与 tested classes 使用独立 copy，
  只恢复完整审计出的 verifier-private 权限，保持 extracted artifact 不变，
  并形成 deterministic same-/cross-filesystem 回归保护。
- signoff_scope: portable replay/release tooling contract repair only；不签收
  replacement Step 5、新 Step 7 或 9.3.4 final authority，不授权 tag、release
  或 publish。
- historical_failure: Step 7 r1 at
  `62bf3fe1456af01179f09e2a9a80c3d229e2435f` remains immutable
  `candidate-passed`; semantic replay errors were `E_RUN_CONTEXT` and
  `E_CHILD_LIFECYCLE`.

## Acceptance Basis

- approved canonical spec: status=`READY_FOR_SIGNOFF` before this record。
- changed paths / diff: private full-clone branch
  `codex/v934-step7-semantic-portable-replay-fix`; implementation surface is
  `portable_replay_tool.py`, release-gate self-test dispatch, Step 4/5/6 hash
  closure and `docs/9.3.4`。
- focused evidence:
  `/tmp/v934-step7-replan-focused-20260724` plus final fresh outputs
  `/tmp/v934-step7-replan-final-ci-20260724-r1` and
  `/tmp/v934-step7-replan-final-coverage-contract-20260724-r1.json`。
- protected workspace evidence: original workspace remained at
  `9743f97d9d935d5e26311b78c158755bca51f17a` with only its pre-existing
  `docs/9.3.5` dirty changes。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 mode audit | enumerate every normalized/canonical mode conflict without wildcard | exact conflict set is `child-ready/{unit,integration,step3-required}.json: 0644 -> 0600`; all other governed exact modes remain `0644` | static audit of release normalization and all bounded coverage mode checks | pass |
| AC-2 copy identity | evidence/classes always independent, regular and single-link | exclusive-create copy only; source/target inode differs; target `st_nlink==1`; hardlinked inputs rejected | same-FS evidence/classes and distinct-device self-test | pass |
| AC-3 source and permissions | restore canonical modes only in separate target; extracted tree unchanged | exact three-path policy; final extracted entry identity/metadata snapshot equals initial snapshot | materialization assertions, source mutation negative, coverage lifecycle verifier tests | pass |
| AC-4 receipt | deterministic copy totals and named/versioned permission policy | per-tree/totals copies, `hardlinks=0`, permission items/applied counts, source/target hashes | same-/cross-FS receipts compare equal | pass |
| AC-5 fail-closed matrix | cover policy drift, wrong mode, hardlinks, mutation and target collision | replay negatives=`9/9` | portable replay self-test JSON | pass |
| AC-6 closure/regression | update governed hashes; keep affected negatives green | Step 4/5/6 manifests pass; artifact=`105`, coverage XML=`130`, coverage contract=`28`, package=`120`, pointer=`5+66`, CI contract=`86` | actual command outputs and fresh result files | pass |
| AC-7 documentation/readiness | record implementation/evidence and obtain non-self approval | BUG/result, parent feature and progress updated; repository owner explicitly approved continuation | canonical docs and user approval | pass |

## Implementation Quality

- scope and changed surface: no production Java, API/SPI, POM, database,
  coverage threshold/exclusion, release normalization or GitHub workflow change。
- maintainability and duplication: one frozen
  `v934-semantic-replay-canonical-materialization-v1` policy owns the exact
  child set and receipt representation；materialization has one copy path。
- error handling and edge cases: source races, hardlinked inputs, destination
  collision, missing/extra/wrong policy, receipt drift and cross-device copying
  all fail closed with typed errors。
- contract, data and compatibility: portable receipt top-level schema remains
  version 1 and compatible with pointer validation；new detail is nested under
  the existing `materialized` object。
- terminology and documentation: historical candidate、semantic replay、
  replacement Step 5/new Step 7 and final pointer boundaries remain distinct。

## Evidence Matrix

| Item | Risk | Unit | Integration | E2E | Playwright | Manual | Evidence | Coverage |
|---|---|---|---|---|---|---|---|---|
| independent copy/link count | critical | replay self-test | same-/cross-FS materialization | replacement authority pending by contract | N/A | inode/stat audit | 2 same-FS + 1 cross-FS | covered |
| canonical private modes | critical | 9 policy negatives | coverage lifecycle negative suite | replacement semantic replay pending by contract | N/A | exact mode inventory | coverage XML 130 + static verifier audit | covered |
| source immutability | critical | mutation negative | complete entry snapshot equality | replacement authority pending | N/A | metadata comparison | `E_SOURCE_RACE` plus source-after equality | covered |
| receipt and consumer compatibility | major | receipt drift negative | pointer final-negative | N/A | N/A | schema audit | replay 9 + pointer 5/66 | covered |
| hash/static closure | major | manifests | CI contract negative | N/A | N/A | workflow diff audit | manifests + CI 86 + 4 unchanged workflows | covered |

## Independent Static and Evidence Verification

- `python3 -m py_compile` and `bash -n scripts/verify-v934-release-gate.sh`
  passed。
- `portable_replay_tool.py self-test --cross-filesystem-root /dev/shm`
  passed with `same_filesystem_checks=2`, `cross_filesystem_checks=1` and
  `negative_cases=9`。
- `sha256sum -c` for Step 4/5/6 passed after the final implementation byte
  cleanup；portable tool digest is
  `2c2e4888f180720d6130b46e5fa6eb31589f164d897bf94b18a289ba32cab479`。
- CI contract negatives passed `86/86`; `validate-workflows` passed with
  `16` tooling paths and `4` unchanged workflows。
- coverage contract negatives passed `28/28`; the previously executed
  coverage XML, artifact, package and pointer matrices remain reusable because
  only a no-behavior fixture assignment cleanup occurred afterward and the
  final replay/manifests/CI closure were rerun。
- `git diff --check` passed and `.github` changed-path audit is empty。

## Evidence Reuse Decision

- Product/test selection and coverage verifier bytes did not change；the
  existing affected negative matrices remain applicable。
- The final portable tool byte change only removed two overwritten local
  fixture assignments；portable self-test, compile, manifests, CI contract and
  coverage contract were rerun afterward。
- Replacement Step 5 and new Step 7 are authority attempts, not missing
  implementation tests for this BUG；their source/artifact identity must be
  fresh and cannot reuse r1。

## Failed Items

- none

## Risks / Follow-ups

- The repair changes governed tool bytes, so the historical Step 5/Step 7
  source seals are not reusable。
- Exactly one replacement Step 5 rehearsal and exactly one new Step 7 authority
  remain mandatory；their attempt budgets are currently unconsumed。
- No final authority pointer may be generated before fresh semantic replay and
  the ordered authority review chain pass。

## Final Decision

- decision: accepted
- rationale: all seven repair acceptance criteria have actual static,
  same-/cross-filesystem, mutation and affected-contract evidence；the change is
  confined to the approved tooling boundary, preserves verifier security and
  extracted artifact identity, and has no blocker or unknown critical evidence。
- blocking_items: none
- follow_up_owner_and_due: release owner；proceed through exact clean/pushed
  repair source, replacement Step 5, its independent acceptance, then a
  separately activated new Step 7。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: repository-owner-via-user-approval
- signed_off_at: 2026-07-23
- acceptance_record:
  `docs/9.3.4/acceptance/BUG-step7-semantic-portable-replay-tool-contract-signoff-20260723.md`
- blocking_items: none
- follow_up_required: yes
