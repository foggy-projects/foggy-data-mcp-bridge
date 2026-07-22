---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-V934-FSSCRIPT-LIFECYCLE-INVENTORY-DUAL-SEAL-SUCCESSOR-REPLAN
status: ULTRA_EXECUTING
canonical: true
execution_mode: ultra
approved_by: project-owner-via-explicit-start-steps-1-through-8
approved_at: 2026-07-22
controlling_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-V934-FSSCRIPT-LIFECYCLE-INVENTORY-SOURCE-SEAL-SUCCESSOR-REPLAN
  - BUG-V934-FSSCRIPT-LIFECYCLE-REPORT-INVENTORY-SUCCESSOR-REPLAN
open_questions: []
---

# Delivery Spec: FSScript lifecycle inventory dual-seal successor replan

## Document Purpose

- intended_for: ultra-implementation / independent release-authority signoff.
- purpose: 原子同步 FSScript lifecycle validator 对 canonical Step4 outer runner 的 raw 与
  executable-stream 两个 stale consumer，保持所有 fail-closed 保护，并恢复唯一新 Cdiag 的
  v9.3.4 release-authority 路径。
- canonical_path:
  `docs/9.3.4/workitems/BUG-v934-fsscript-lifecycle-inventory-dual-seal-successor-replan.md`

## Goal

- version_goal: 让已接纳 `774+59/5709` FSScript report inventory 的 outer runner 同时满足
  canonical raw identity、top manifest、nested raw seal 和 reviewed executable-stream seal。
- target_outcome: 在 activation Cdiag 前通过 exact raw binding、完整 lifecycle positive/negative
  suite 及 Step4/Step6 CI closure；随后用唯一新 run ID 完成 fresh all-lane diagnostic，并仅在
  成功后推进 Cfreeze、formal 和 Step5-7。

## Scope

- in_scope:
  - 将 lifecycle validator 的 `OUTER_RUNNER_SHA256` 从
    `cf3979fc76ff4369eca88ce52e35dda66ead98f3579d09e1482646de3f109e82`
    更新为 canonical outer raw SHA-256
    `ccda7e7e78547a30ada3466df76d197c483ddd3d16be3f43dcad0ff47e37a57e`；
  - 将 `OUTER_EXECUTABLE_STREAM_SHA256` 从
    `f3d3587486e16a54c90af381554c2115c0188aaec28cc631717dd01f5c66a16a`
    更新为当前 reviewed executable physical/logical stream SHA-256
    `c696f9bf34e76bd3a5cae97d28ed378cae2cc78b872fc9c948985afc5c763a0c`；
  - 保留 Unit、Integration、outer、library 四方 raw/executable-stream seal、descriptor-bound
    strict-read、shape/order/slice checks 及全部 mutation negatives；
  - 更新 lifecycle tool 的 Step4 manifest row，并重算 Step4 manifest、依赖该 identity 的 Step6
    CI contract/tool/manifest 及所有实际发生的传递 digest；
  - 在 integration commit 前依次运行 exact raw binding、独立 executable-stream 复算、完整
    lifecycle suite、Step4/6 manifests、coverage contract、successor overlay、CI workflow 与
    86-case negative focused validation；
  - focused 全绿后创建 diagnostic-ready integration、只含本文件两字段 activation 的单父 Cdiag，
    并从 fresh non-shallow clone 使用唯一新 run ID 执行一次 diagnostic；成功后按既有治理推进
    Cfreeze、formal、Step5-7。
- affected_modules:
  - `scripts/v934/step4` lifecycle validator 与 Step4 manifest；
  - `scripts/v934/step6` CI contract/tool/manifest；
  - `docs/9.3.4` 本 successor 状态和后续 evidence。
- external_dependencies: GitHub PR #124、Maven、Docker/Compose、既有五数据库 authority 环境；
  不发布制品、不部署业务环境。

## Non-Goals

- out_of_scope:
  - 修改 outer/Unit/Integration runner 或 lifecycle library 的 executable commands、控制流或业务
    语义；
  - 修改 FSScript 产品/测试代码、Spring 生命周期修复、报告 amendment、`774+59/5709` 基数、
    coverage floor/exclusion、selector、fork、skip、测试顺序、DB set、POM 或 public API/SPI；
  - 放宽、删除、绕过 raw/executable-stream seal、strict-read、mutation negative 或 fail-closed
    语义；
  - 复用 r6 Cdiag/run ID、上一轮失败 focused invocation、partial authority、candidate 或 capsule；
  - 扩大到方案 B/C、v9.3.5 或 v9.4.0。
- do_not_touch:
  - 原工作区 `docs/9.3.5/README.md` 和 `docs/9.3.5/workitems/`；
  - `foggy-fsscript/src/main`、`foggy-fsscript/src/test`、launcher 产品/测试字节；
  - historical Step2 parent、旧 evidence/run roots、未知 host 资源和非 run-owned Docker 资源。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| raw 与 executable-stream 两个 stale consumer 同一原子修复 | `5245f2de` 同时改变 outer raw bytes 与 reviewed executable lines；前一 Cplan 只修 raw 后被第二封印正确拒绝 | outer runner 本身不改字节，两个 observed target 必须独立复算一致 |
| 保持四方双层 seal fail closed | 两次失败都是保护机制正确工作，不是门槛过严 | positive 必须通过，现有 raw/semantic mutations 不减少 |
| 完整 lifecycle suite 是 activation gate | exact raw preflight 不覆盖 executable-stream consumer | suite 未最终 PASS 不得创建 integration/Cdiag |
| 产品证据最小失效半径 | 产品、测试、报告基数和 Step5 tooling 均未改变 | focused 阶段不额外跑 Maven 全量；all-lane 只在唯一 diagnostic 中运行 |
| 新 integration/Cdiag/run ID | r6 与失败 focused invocation 永久 excluded | 不拼接、不重试；新昂贵运行失败即记录并转 `NEEDS_REPLAN` |

## Acceptance Criteria

- [x] AC-1: 本 Cplan 是 failure-record commit
  `41ade125fa4964c97a7446637588d8c9b08e2414` 的 clean/pushed 直接子提交；原工作区不变。
- [x] AC-2: nested outer raw seal 精确等于 canonical raw bytes 与 Step4 manifest；nested outer
  executable-stream seal 精确等于独立复算值；其他六个 seal 常量不变。
- [x] AC-3: exact binding PASS `count=4`，完整 lifecycle suite 最终 exit 0/PASS，既有 positive、
  raw mutation、semantic mutation、shape/order/slice case 数量和拒绝码不减少。
- [x] AC-4: Step4/6 manifests、coverage contract、successor overlay、CI contract/workflows 与
  86-case negative 全部闭合；Step5 bytes/manifest、报告基数和 coverage policy 不变。
- [x] AC-5: diagnostic-ready integration diff 仅包含本 work item implementation result 和批准的
  5 个 tool/hash closure 文件；不含产品/测试/POM/outer runner/report amendment。
- [ ] AC-6: activation Cdiag 是 integration 的直接单父子，diff 仅本文件 `status/readiness`
  两字段 `APPROVED -> ULTRA_EXECUTING`；fresh clone clean、non-shallow、detached exact HEAD。
- [ ] AC-7: 唯一新 diagnostic 使用新 run ID，完成 required lanes、source before/after、report
  inventory、coverage、sensitive、cleanup、candidate/capsule；所有 F/E/S 为 0。
- [ ] AC-8: 仅 AC-7 成功后推进 direct-child Cfreeze、fresh formal、Step5-7；任一新昂贵运行
  失败均记录 failed/excluded、设置 `NEEDS_REPLAN` 并停止，不自动重试。

## Contract / Data / Security Constraints

- API or event contract: 无产品 API/SPI、Spring Bean、配置、事件或 evidence schema 变化；仅
  existing validator constants 和传递 digest 值变化。
- data and migration: 无数据库 schema、业务数据或用户数据迁移；仅 run-owned fixture/evidence。
- compatibility and rollback: integration 可独立 revert，但 revert 会恢复已证明的 deterministic
  dual-seal failure；不得通过关闭 executable-stream verifier 回滚。
- permissions and secrets: 限于已授权 v934 runner、evidence 目录和 PR #124；持久记录不保存
  凭证、raw output、host path 或外部资源 identity。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1..AC-2 | blocker | Git topology/paths、raw 三方 comparison、独立 executable-stream 复算 | exact Cplan/Cint SHA 与四个 digest observations |
| AC-3 | blocker | exact binding、完整 lifecycle suite、既有 source/semantic mutations | `count=4`、最终 PASS line、exit 0、无残留进程 |
| AC-4..AC-5 | critical | Step4/6 manifests、coverage/overlay、CI workflows 和 86-case negative、diff audit | validator summaries、manifest hashes、protected-path proof |
| AC-6 | blocker | Cdiag parent/path/two-field diff、fresh-clone preflight | exact Cint/Cdiag SHA、clean/non-shallow proof |
| AC-7 | blocker | 一次 fresh full Step4 diagnostic | run ID、lane/report/coverage/candidate/capsule/cleanup receipts |
| AC-8 | blocker | 成功后的 Cfreeze/formal/Step5-7 或失败记录 | commit/run IDs、verdict、停止边界 |

验证成本与循环控制：

- `<5m`: SHA/manifest、syntax、diff、exact binding、独立 stream 复算、coverage/overlay/CI static；
  相关字节变化后重跑。
- `5-30m`: 完整 lifecycle suite 和 CI 86-case negative；在 integration 字节冻结后各最多一次。
- `>60m`: fresh diagnostic、formal 及后续 release-authority；每个 governed run ID 最多一次。
- reusable evidence: 已签收 FSScript lifecycle 产品测试、report inventory `774+59/5709`、Step5
  package 117-case negative 及不依赖 Step4 manifest identity 的既有 focused 证据继续有效。
- rerun trigger: 只有 source/tooling/policy/selector/environment assumption 或 evidence identity
  改变才使对应结果失效。
- stop/replan: exact/stream/lifecycle gate、Cdiag topology 或任何昂贵运行失败，均不得原地修补
  重试；记录 fail-closed 后设 `NEEDS_REPLAN`。

## Bug Context

- bug_source: regression-found during release-authority focused validation.
- severity: critical/blocker.
- environment: PR #124；failure base `41ade125...`; outer runner produced by `5245f2de...`。
- current_behavior: outer raw actual/top manifest=`ccda7e7e...e37a57e`，nested raw=
  `cf3979fc...f109e82`；outer executable stream observed=`c696f9bf...c763a0c`，nested expected=
  `f3d35874...f66a16a`。修正 raw 后完整 lifecycle suite 以 `E_EXECUTABLE_STREAM_SEAL` 退出 1。
- expected_behavior: 两个 nested outer consumer 分别精确匹配 canonical raw bytes 和 independently
  computed reviewed executable stream，所有 mutations 继续 fail closed。
- reproduction_steps:
  1. 比较 outer raw、Step4 manifest 与 nested `OUTER_RUNNER_SHA256`；
  2. 运行 exact binding，观察 stale raw 被拒绝；
  3. 临时同步 raw/manifest closure 后运行完整 lifecycle suite；
  4. 观察 outer stream expected `f3d358...`、observed `c696f9...`。
- reproduction_status: confirmed.
- existing_evidence:
  `docs/9.3.4/evidence/step-4/step4-v934-fsscript-lifecycle-inventory-source-seal-successor-focused-lifecycle-fail-closed.md`。
- existing_tests: descriptor-bound four-runner raw binding、complete lifecycle suite、raw/semantic/
  shape negatives、Step4/6 manifest 和 CI negative。
- regression_protection: required；不得删除现有自动化，本 successor 只更新 frozen reviewed
  identities 并把完整 suite 固定为 activation gate。
- waiver_reason_and_risk: N/A。

## Risks and Open Questions

- known_risks:
  - lifecycle tool hash 会传递至 Step4 manifest，再传递至 Step6 CI contract/tool/manifest；遗漏任一
    consumer 会再次 fail closed；
  - independently recomputed stream 若不等于 observed `c696f9...`，批准假设失效，必须 replan；
  - full diagnostic 仍受 Docker/端口/runner image 环境影响，但一次性规则不区分环境与产品失败。
- open_questions: none

## Ultra Execution Contract

- 先提交并 push 本 `APPROVED` Cplan；它不授权 runner。
- 仅允许一个 diagnostic-ready integration commit；先独立复算 stream，再按顺序执行 exact
  binding、完整 lifecycle、manifest/coverage/overlay/CI focused validation。
- integration clean/pushed 后创建唯一 activation Cdiag；其 diff 只能是本文件两字段变化。
- 只从 Cdiag fresh non-shallow clone 启动一次新 diagnostic，建议 run ID
  `step4-v934-fsscript-lifecycle-dual-seal-diagnostic-20260722-r7`。
- 如需修改 outer runner、产品/测试/POM/report inventory/coverage/selector/DB set、安全边界，
  或任一昂贵运行失败，将本文件设为 `NEEDS_REPLAN` 并停止。
- 完成后填写 `Implementation Result` 并设为 `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

> Focused implementation is complete and diagnostic-ready. Status remains `APPROVED`; only a
> separate two-field activation Cdiag may authorize the governed runner.

- implementation_summary: atomically synchronized the nested outer raw SHA and reviewed
  executable-stream SHA, then recomputed the Step4 manifest and Step6 CI contract/tool/manifest
  digest closure. The canonical outer runner itself was not modified.
- changed_paths:
  - this canonical work item;
  - `scripts/v934/step4/run_log_lifecycle_negative_test.sh`;
  - `scripts/v934/step4/SHA256SUMS`;
  - `scripts/v934/step6/ci-contract.json`;
  - `scripts/v934/step6/ci_contract_tool.py`;
  - `scripts/v934/step6/SHA256SUMS`.
- tests_and_results:
  - independent executable-stream recomputation=`c696f9bf34e76bd3a5cae97d28ed378cae2cc78b872fc9c948985afc5c763a0c`;
  - canonical raw binding=`status=passed count=4`；Step4/Step6 manifests、Bash/Python/JSON syntax、
    `git diff --check`=PASS；
  - complete lifecycle suite=exit 0/PASS，保持 `runner-seal-binding=1+6`、Unit shape=16、
    Integration shape=14、semantic seal=2+5、source seal=3、outer/library shape=4+3；
  - coverage contract=`774+59/5709`, `23 exec/48 sessions`, status=passed；successor overlay=passed；
    workflows=`4`, status=passed；CI negative=`86/86`, result SHA-256=
    `9ba9c1d403ae269b543b72bfe8b1ef18b6bd5c773ff023a3becaf844d7b44638`；
  - no lifecycle helper/persistent-child remained after the suite.
- manual_or_experience_evidence: outer raw remained
  `ccda7e7e78547a30ada3466df76d197c483ddd3d16be3f43dcad0ff47e37a57e`; lifecycle tool=
  `8ce0fd26b335ddad899178da760ee9bd8b77395b7ef0bb35f9a3cdffe77a17e3`; Step4 manifest=
  `704b31cbd274578f4ca5e8d491b609c151f2026a84198bc479e24cb55ee956f3`; Step6 manifest=
  `a1bb362888057201a9f674e66711c33be79a4b8e6fd7beebbef94ce55d38ff64`；Step5 manifest unchanged=
  `e8aaa30f853ce723e6d2b9b09ce5e7241b3638b2748f80d55ae04a438624f4c7`.
- deviations: none
- residual_risks: fresh all-lane diagnostic and subsequent formal/Step5-7 remain pending and retain
  their existing Docker, port and external-environment risks.
- readiness: ULTRA_EXECUTING

## References

- predecessor work item:
  `docs/9.3.4/workitems/BUG-v934-fsscript-lifecycle-inventory-source-seal-successor-replan.md`
- focused fail-closed evidence:
  `docs/9.3.4/evidence/step-4/step4-v934-fsscript-lifecycle-inventory-source-seal-successor-focused-lifecycle-fail-closed.md`
- historical binding bug:
  `docs/9.3.4/workitems/BUG-step4-outer-runner-source-seal-binding-drift.md`
- controlling release item:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
