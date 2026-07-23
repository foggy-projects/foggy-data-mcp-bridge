---
acceptance_scope: feature
version: 9.3.4
target: v934-step5-local-rehearsal-no-ci-fullclone-r2
status: signed-off
decision: accepted
signed_off_by: independent-release-reviewer
signed_off_at: 2026-07-23
reviewed_by: independent-release-reviewer
blocking_items: []
follow_up_required: yes
evidence_count: 18
---

# Step 5 no-CI full-clone replacement rehearsal signoff

## Document Purpose

- intended_for: release owner / project root session
- purpose: 对 PR #124 在
  `c6654042d0b4f7b9abdd2e524c64e451e070e90a` 提交的 Step 5 replacement
  rehearsal closure 形成独立、可复核的正式签收结论。

## Background

- delivery_spec:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- target_outcome: 接受 exact tested commit 上的单一 no-CI rehearsal candidate、
  portable archive、Launcher JAR/runtime-image identity 和 fail-closed negatives。
- signoff_scope: replacement Step 5 only；不签收 Step 7 或 9.3.4 final authority，
  不授权 merge、tag、release 或 publish。
- audited_pr_head:
  `c6654042d0b4f7b9abdd2e524c64e451e070e90a`
- tested_commit:
  `6c3ee97abbe49c0cf5cf485d2ddeb4ba7ff7c84f`
- run_id: `step5-local-rehearsal-20260723-no-ci-fullclone-r2`

## Acceptance Basis

- approved canonical spec：status=`READY_FOR_SIGNOFF` at audited PR head。
- execution evidence:
  `docs/9.3.4/evidence/step-5/step5-local-rehearsal-no-ci-fullclone-r2-passed-20260723.md`
- preflight evidence:
  `docs/9.3.4/evidence/step-5/step5-local-rehearsal-no-ci-fullclone-r2-preflight-20260723.md`
- preserved raw evidence：private full clone 的
  `target/v934-release-gate/runs/step5-local-rehearsal-20260723-no-ci-fullclone-r2/`
  及同 run 的 Step4、Unit、Integration、required/five-DB、External、Addon
  run roots。
- live repository evidence：PR state/head/status checks、Actions permissions 与
  active runs、branch protection/rulesets、tag/release 查询。
- protected workspace evidence：原工作区 Git status、9.3.5 README content hash、
  untracked workitem remote-absence 和 closure changed-path audit。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| Identity seal | tested commit、run id、source seal 和 candidate pointer 精确同源 | pointer=`6c3ee97a…/fullclone-r2`，summary SHA=`31a67b73…`；source before/after=`f0a33a72…`，4298 files | candidate pointer、summary、source JSON/TSV、pointer tool verification | pass |
| Lane totals | Step4、Unit、Integration、五库、External、Addon 数字一致且 F/E/S=0 | Step4=`23/48`、required=`774+59/5709`；Unit=`682+55/4943`；Integration=`47+4/320`；DB=`29/370`；External=`16/76`；Addon=`2/6` | raw lane summaries and run-status files | pass |
| Artifact identity | archive/JAR/image、异目录验证和 negatives 一致 | archive=`36245c87…`；JAR=image `/app/app.jar`=`14aac413…`；image=`d109c64d…`；artifact/package/pointer negatives=`105/120/5` | raw manifests、archive verify、download-verify、negative results | pass |
| Authority pointers | final/authority pointers must remain absent | `final-run.env`、`authority-run.env`、`final-authority-run.env` 在 target root 和 run root 均 absent | exact presence probes；summary `final_authority_pointer_updated=false` | pass |
| no-CI boundary | Actions disabled；CI 不作为 authority | live Actions=`enabled=false`，queued/in-progress=`0/0`；PR status checks empty；historical remote runs excluded | GitHub API/PR audit、canonical spec、execution evidence | pass |
| Portability boundary | semantic portable replay remains downstream | `portable_byte_verify=passed`；`portable_semantic_replay=required-downstream` | summary、execution evidence、confirmed decision | pass |
| Authorization boundary | Step7、merge、tag、release、publish not authorized | PR remains open；no 9.3.4 tag/release；canonical authorization explicitly excludes all five actions | spec、PR/tag/release audit | pass |
| Protected workspace | original 9.3.5 dirty changes not mixed | closure diff contains only `docs/9.3.4`；remote README equals original `9743f97d…` baseline, not dirty working content；untracked workitem absent remotely | Git diff/tree/hash/status audit | pass |

## Implementation Quality

- scope and changed surface: `6c3ee97a..c6654042` 仅修改四个
  `docs/9.3.4` execution-closure paths；无 production、test、POM、runner、
  manifest、workflow 或 `docs/9.3.5` 变更。
- maintainability and duplication: canonical spec、progress、README 和单一 execution
  evidence 采用同一 run/tested-commit/count/identity 口径，无第二 authority schema。
- error handling and edge cases: pointer publication 的 final-scope refusal、pre-commit
  failure、rollback 与 symlink refusal 全绿；artifact/package mutation/absence
  negatives 保持 fail closed。
- contract, data and compatibility: public API/SPI、数据库 fixture contract、
  coverage floor/exclusion、Actions/branch settings 和 release publication均未改变。
- terminology and documentation: rehearsal candidate、final authority、portable byte
  verify 和 downstream semantic replay 边界明确，未把 candidate 提升为 Step7。

## Evidence Matrix

| Item | Risk | Unit | Integration | E2E | Playwright | Manual | Evidence | Coverage |
|---|---|---|---|---|---|---|---|---|
| tested identity and source seal | critical | N/A | same-run binding | N/A | N/A | Git/pointer/hash audit | raw pointer、summary、source seals | covered |
| Step4 and lane totals | critical | `682+55/4943` | `47+4/320` | DB/external/addon governed lanes | N/A | summary cross-check | raw summaries/run-status/final manifest | covered |
| archive/JAR/image portability | critical | artifact self-test 105 | package negatives 120 | image/archive identity | N/A | different-directory evidence audit | archive/package/image manifests and verifier output | covered |
| pointer/CI/authorization boundary | critical | pointer negatives 5 | N/A | N/A | N/A | live GitHub and exact absence probes | pointer tool、API、spec | covered |
| protected workspace isolation | major | N/A | N/A | N/A | N/A | status/tree/hash/diff audit | original workspace plus audited closure range | covered |

## Independent Static and Evidence Verification

- `pointer_tool.py verify-candidate`：status=`passed`，exact run id/tested commit/mode；
  pointer SHA-256=`6f1c30418c147b92ec313bcf4b67fc397f9e6d38f6972465b0b2e5f84c4c583f`。
- archive digest `sha256sum -c`：pass；独立 `verify-archive`：status=`passed`，
  `8303 files / 9424 entries / 528541789 bytes`，archive/JAR/root/file-manifest
  hashes与 execution record exact。
- raw source-before/source-after JSON 和 TSV exact；runtime-source before/after
  byte-identical，13 modules / 1411 files / 10,757,069 bytes。
- raw Step4 final verifier、run-status和所有 lane summaries一致；算术闭包为
  `682+47+29+16=774` executions、`55+4=59` structural、
  `4943+320+370+76=5709` testcase，Addon 独立为 `2/6`。
- image manifest 同时绑定 tested JAR、two-file build context 与 embedded
  `/app/app.jar`；三者 SHA-256 exact，cleanup flags全为 true。
- existing `download-verify` 与 `staging` 为不同绝对目录；其
  `extract-verify` record与 archive verifier hashes exact。
- live PR #124 head精确为 audited head且 state=`OPEN`；Actions disabled，
  queued/in-progress=`0/0`，status check rollup empty；main无 branch protection，
  repository无 active ruleset。
- 原工作区保持 `9743f97d…` dirty baseline。9.3.5 README：working hash=
  `1cce7966…`，baseline/remote audited head hash均=`8ba4be7d…`；untracked
  `OPT-v934-release-gate-checkpoint-resume-wsl-capsule.md` 在 audited remote tree
  absent。

## Evidence Reuse Decision

- tested production/test/runner/tool/manifest bytes、test selection、关键输入和
  artifact identity自 replacement run 后未变化；audited head仅增加
  `docs/9.3.4` closure documentation。
- preserved raw run roots、candidate pointer和archive均可独立读取并通过只读
  pointer/archive验证；未发现真实性、计数、身份或契约不一致。
- 按批准的最小重验证半径，本次未重跑一小时级 Step4 或 Step5，也未运行 Maven、
  database lanes 或 Docker package/image build。

## Failed Items

- none

## Risks / Follow-ups

- semantic portable replay仍为 downstream；本签收只接受 governed byte-level
  portable archive verification。
- Actions/required checks/branch protection按 owner 决策继续缺席；该流程风险不阻断
  本地 Step 5 acceptance。
- Step7 exact-clean-main authority、ordered reviews和9.3.4 final version signoff
  仍 pending，必须取得单独授权。

## Final Decision

- decision: accepted
- rationale: replacement Step 5 的关键 criteria 均有 preserved raw evidence、
  deterministic hashes、只读独立验证和 live governance audit 支持；八项必确认
  全部通过，无失败、未知证据、范围偏离或阻断风险。
- blocking_items: none
- follow_up_owner_and_due: release owner；仅在单独授权后进入 Step7，不设本签收
  内的自动后续动作。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: independent-release-reviewer
- signed_off_at: 2026-07-23
- acceptance_record:
  `docs/9.3.4/acceptance/step5-local-rehearsal-no-ci-fullclone-r2-signoff-20260723.md`
- blocking_items: none
- follow_up_required: yes；Step7 remains separately authorized and pending.
