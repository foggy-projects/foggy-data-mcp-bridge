---
acceptance_scope: feature
version: 9.3.4
target: v934-step5-semantic-replay-replacement-r1
status: signed-off
decision: accepted
signed_off_by: repository-owner-via-user-approval
signed_off_at: 2026-07-24
reviewed_by: codex-delivery-signoff-audit
blocking_items: []
follow_up_required: yes
evidence_count: 10
---

# Step 5 semantic-replay replacement rehearsal signoff

## Document Purpose

- intended_for: release owner / project-root session
- purpose: 对 semantic portable replay 修复后的唯一 replacement Step 5
  rehearsal 形成正式、可复核的签收结论。

## Background

- delivery_spec:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- target_outcome: 接受修复后 source seal 上的 immutable rehearsal candidate、
  complete same-run Step 4 evidence、portable archive、Launcher JAR/runtime
  image identity 和 deterministic semantic replay regression。
- signoff_scope: semantic-replay replacement Step 5 only；不签收新 Step 7
  authority 或 9.3.4 final authority，不授权 tag、release 或 publish。
- audited_head:
  `82b1b4ebcfafbfc9364d2cdd1c0082a22033bdaa`
- tested_commit:
  `b9b8adfd725399cf069dd4165582b7d2e8af4b39`
- run_id:
  `step5-semantic-replay-replacement-20260723-r1`

## Acceptance Basis

- approved repair:
  `docs/9.3.4/acceptance/BUG-step7-semantic-portable-replay-tool-contract-signoff-20260723.md`
- replacement execution evidence:
  `docs/9.3.4/evidence/step-5/step5-semantic-replay-replacement-r1-passed-20260723.md`
- activation/preflight evidence:
  `docs/9.3.4/evidence/step-5/step5-semantic-replay-replacement-r1-preflight-20260723.md`
- preserved raw evidence:
  `target/v934-release-gate/runs/step5-semantic-replay-replacement-20260723-r1/`
- live repository evidence: local/upstream/remote branch identity, Actions
  permission and active-run checks, candidate pointer verification and exact
  final-pointer absence probes。
- protected workspace evidence: original workspace exact HEAD and pre-existing
  `docs/9.3.5` status。
- owner decision: repository owner explicitly replied `接受，继续` on
  `2026-07-24`。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| Tested identity | one pushed tested commit, run id and before/after source seals | tested commit=`b9b8adfd…`; run=`step5-semantic-replay-replacement-20260723-r1`; source and runtime-source seals exact | candidate pointer, summary, source/runtime scans | pass |
| Complete rehearsal | same-run Step 4 and all required lanes pass without failure/error/skip | aggregate=`23 exec / 48 sessions`; required=`774+59 / 5709 / F0E0S0` | final manifest, lane summaries, run statuses | pass |
| Coverage | frozen line and branch minima are met without verifier relaxation | line=`54630/76834`; branch=`26117/44876`; all 12 critical classes pass | coverage gate and final verifier | pass |
| Semantic replay regression | independent copy, exact private modes, unchanged source and deterministic negatives | same-FS=`2`; cross-FS=`1`; negatives=`9/9`; exact three `0600` restorations | portable replay self-test receipt | pass |
| Artifact identity | archive, Launcher JAR and runtime image identities agree and verify independently | JAR=image=`ed48e51e…`; archive=`613e1cac…`; build/verify/extract-verify pass | package/image/archive manifests and receipts | pass |
| Pointer boundary | candidate exact; all final authority pointers absent | candidate verify=`passed`; three final pointer names absent | pointer tool and exact presence probes | pass |
| Attempt and process ownership | unique attempt consumed by private tmux owner; no retry | budget=`1`, consumed=`true`; tmux pane dead=`1`, status=`0` | activation contract and tmux terminal state | pass |
| Governance boundary | no CI, tag, release, publish or verifier weakening | Actions disabled; queued/in-progress=`0/0`; no prohibited action | live API checks, diff and execution evidence | pass |
| Protected workspace | original dirty workspace remains untouched | HEAD=`9743f97d…`; only pre-existing 9.3.5 changes remain | exact status and path audit | pass |

## Implementation Quality

- scope and changed surface: repair implementation is confined to governed
  portable replay/release tooling, hash closure and `docs/9.3.4`；no production
  API/SPI, Java runtime, POM, coverage threshold/exclusion or workflow change。
- maintainability and duplication: one named/versioned canonical
  materialization policy owns copy and permission restoration；receipt and
  verifier use exact bounded fields。
- error handling and edge cases: hardlinked input, source race, existing
  target, permission policy drift, restored mode drift and receipt drift fail
  closed；same- and cross-filesystem paths are covered。
- contract, data and compatibility: canonical verifier checks remain unchanged；
  extracted artifact bytes and metadata remain unchanged。
- terminology and documentation: rehearsal candidate, downstream authority
  semantic replay and final authority remain explicitly distinct。

## Evidence Matrix

| Item | Risk | Unit | Integration | E2E | Playwright | Manual | Evidence | Coverage |
|---|---|---|---|---|---|---|---|---|
| source/tested identity | critical | N/A | same-run binding | N/A | N/A | Git/hash audit | pointer, summary and source seals | covered |
| Step 4 lanes and coverage | critical | `682+55/4943` | `47+4/320` | DB/external/addon governed lanes | N/A | arithmetic and manifest audit | raw lane summaries and final verifier | covered |
| semantic replay repair | critical | 9 negatives | same-/cross-filesystem checks | downstream authority remains Step 7 | N/A | policy/mode/inode audit | self-test receipt and repair acceptance | covered |
| archive/JAR/image | critical | artifact/package negatives | archive extract-verify | runtime-image identity | N/A | hash audit | package/image/archive manifests | covered |
| pointer/governance boundary | critical | pointer negatives | N/A | N/A | N/A | live GitHub and absence probes | pointer tool, API and exact file checks | covered |
| protected workspace | major | N/A | N/A | N/A | N/A | Git status/path audit | original workspace exact baseline | covered |

## Evidence Reuse Decision

- The replacement run is the single contract-authorized expensive execution。
  Its raw evidence, archive, candidate pointer and tested source are preserved
  and independently readable。
- The post-run audited commit adds only execution-closure documentation；
  production/test/tool bytes, test selection, artifact identity and run
  environment assumptions did not change。
- No additional Step 4/Step 5 rerun is required for this signoff。The
  separately activated new Step 7 authority is the next expensive run and
  cannot reuse the historical Step 7 source seal。

## Failed Items

- none

## Risks / Follow-ups

- The accepted bytes are not yet merged into exact clean `origin/main`。
- The new Step 7 attempt remains unconsumed and must be separately activated
  after exact-main identity, frozen contract and attempt ledger checks。
- Downstream authority semantic replay and the ordered review chain remain
  mandatory before any final authority pointer。
- Actions/required checks/branch protection remain absent by owner decision；
  this is a recorded process risk, not a Step 5 blocker。

## Final Decision

- decision: accepted
- rationale: all replacement Step 5 critical criteria have exact, preserved
  evidence；the deterministic replay repair, complete rehearsal lanes,
  coverage, artifact identity, pointer boundary and protected workspace checks
  pass with no blocker or unknown critical evidence。Repository owner approval
  explicitly accepts the result。
- blocking_items: none
- follow_up_owner_and_due: release owner；proceed to exact-main merge and a
  separately frozen new Step 7 activation，without CI/tag/release/publish。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: repository-owner-via-user-approval
- signed_off_at: 2026-07-24
- acceptance_record:
  `docs/9.3.4/acceptance/step5-semantic-replay-replacement-r1-signoff-20260724.md`
- blocking_items: none
- follow_up_required: yes
