---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-V934-FSSCRIPT-LIFECYCLE-INVENTORY-SOURCE-SEAL-SUCCESSOR-REPLAN
status: APPROVED
canonical: true
execution_mode: ultra
approved_by: project-owner-via-continue-after-r6-fail-closed
approved_at: 2026-07-22
controlling_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-V934-FSSCRIPT-LIFECYCLE-REPORT-INVENTORY-SUCCESSOR-REPLAN
  - BUG-934-STEP4-OUTER-RUNNER-SOURCE-SEAL-BINDING-DRIFT
open_questions: []
---

# Delivery Spec: FSScript lifecycle inventory source-seal successor replan

## Document Purpose

- intended_for: ultra-implementation / independent release-authority signoff.
- purpose: 修复 r6 在任何测试 lane 前暴露的 outer runner nested SHA 漂移，保持既有
  source-seal fail-closed 语义，并使用全新的 integration、Cdiag 和 run ID 恢复 v9.3.4
  release-authority。
- canonical_path:
  `docs/9.3.4/workitems/BUG-v934-fsscript-lifecycle-inventory-source-seal-successor-replan.md`

## Goal

- version_goal: 让已接纳 FSScript lifecycle report 的 Step4 runner 同时满足 top manifest、
  nested lifecycle constant 和 canonical raw bytes 三方 exact binding。
- target_outcome: `run_log_lifecycle_negative_test.sh --verify-runner-seal-bindings` 在 Cdiag 前
  focused preflight 通过；随后从唯一新 Cdiag 完成一次 fresh all-lane diagnostic，并仅在成功后
 继续 Cfreeze/formal/Step5-7。

## Scope

- in_scope:
  - 将 lifecycle validator 的 `OUTER_RUNNER_SHA256` 从 r6 观测到的旧值
    `cf3979fc76ff4369eca88ce52e35dda66ead98f3579d09e1482646de3f109e82`
    更新为 canonical outer runner raw SHA-256
    `ccda7e7e78547a30ada3466df76d197c483ddd3d16be3f43dcad0ff47e37a57e`；
  - 保留 Unit、Integration、outer、library 四方 raw/executable-stream seal 和 descriptor-bound
    strict-read 机制，不删除、不绕过、不降级任何 positive/negative probe；
  - 更新 lifecycle tool 自身的 Step4 manifest row，并重算 Step4 manifest、依赖该 manifest 的
    Step6 CI contract/tool/manifest 及所有实际发生的传递 hash binding；
  - 在 diagnostic-ready integration 提交前显式执行 canonical
    `--verify-runner-seal-bindings`，并运行完整 lifecycle suite、Step4/6 manifests、coverage
    contract、successor overlay、CI workflow/negative focused 验证；
  - 创建新的单父 activation Cdiag，diff 仍只允许本文件 `status/readiness` 两字段变化；从
    fresh non-shallow clone 使用新 run ID 执行一次 diagnostic。
- affected_modules:
  - `scripts/v934/step4` lifecycle source-seal validator 和 Step4 manifest；
  - 依赖 Step4 manifest identity 的 `scripts/v934/step6` CI contract/tool/manifest；
  - `docs/9.3.4` successor work item 和后续 evidence。
- external_dependencies: GitHub PR #124、Maven、Docker/Compose 和既有五数据库 authority
  环境；不发布制品、不部署业务环境。

## Non-Goals

- out_of_scope:
  - 修改 outer runner、Unit/Integration runner、lifecycle library 的业务逻辑或 executable
    stream；
  - 修改 FSScript 产品/测试代码、报告 amendment、`774+59/5709` cardinality、coverage floor、
    exclusion、selector、fork、skip、测试顺序、DB set、POM 或 public API/SPI；
  - 重试或复用 r6 Cdiag、run ID、preflight 绿色、candidate、capsule或任何 partial authority；
  - 重新设计 source-seal 机制，或扩大到方案 B/C、v9.3.5、v9.4.0。
- do_not_touch:
  - 原工作区 `docs/9.3.5/README.md` 和 `docs/9.3.5/workitems/`；
  - `foggy-fsscript/src/main`、`foggy-fsscript/src/test`、launcher 产品/测试字节；
  - historical Step2 parent、旧 r5/r6 evidence/run roots、未知 host 资源和非 run-owned
    container/volume/network。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 只同步 nested outer raw SHA | r6 已证明 actual outer 与 top manifest 一致，唯一漂移是 lifecycle consumer 的旧常量 | 不改 runner 语义、executable-stream seal 或产品字节 |
| 保持三方 exact binding fail closed | r6 的拒绝是正确保护，不是需要放宽的门槛 | canonical positive 必须通过，现有 mutation negatives 不减少 |
| focused preflight 必须显式调用 exact binding | 上轮静态契约未执行该入口，导致确定性漂移进入 Cdiag | 新 Cdiag 前必须观测 stable PASS；不能只依赖 manifest `sha256sum -c` |
| 使用新 integration/Cdiag/run ID | r6 已被一次性规则永久排除 | 不拼接、不重试；失败再次转 `NEEDS_REPLAN` |
| 复用不受影响的产品与 package focused 证据 | 产品、测试、报告基数和 Step5 tooling 均不改变 | 只有 hash identity/CI closure 相关证据失效并重跑 |

## Acceptance Criteria

- [ ] AC-1: 本 Cplan 是 failure commit
  `74ea896a3a0dfb0cf7182c39fe1534e74f7fb84b` 的 clean/pushed 直接子提交；原工作区不变。
- [ ] AC-2: lifecycle validator 的 outer raw constant 精确等于 canonical outer bytes 和 top
  manifest row；其他三个 raw seal、四个 executable-stream seal 和 strict-read 语义不变。
- [ ] AC-3: canonical binding preflight PASS，完整 lifecycle regression 保持原 exact positive/
  negative 数量且 F/E/S/skip policy 不变；outer runner 不因本事项产生字节变化。
- [ ] AC-4: Step4/6 manifests、coverage contract、successor overlay、CI contract/workflow/negative
  全部闭合；Step5 bytes/manifest、报告基数和 coverage policy 不变。
- [ ] AC-5: diagnostic-ready integration diff 不含产品/测试/POM/Step2 parent/report amendment；
  public API/SPI 和生产语义无变化。
- [ ] AC-6: 唯一新 activation Cdiag 是 integration 的直接单父子，diff 仅本文件两字段
  `APPROVED -> ULTRA_EXECUTING`；fresh clone clean、non-shallow、detached exact HEAD。
- [ ] AC-7: 唯一新 all-lane diagnostic 使用新 run ID，完成 required lanes、source before/after、
  report inventory、coverage、sensitive、cleanup、candidate/capsule；所有 F/E/S 为 0。
- [ ] AC-8: 仅在 AC-7 成功后推进 direct-child Cfreeze、fresh formal、Step5-7；任何新昂贵
  运行失败均记录 failed/excluded、设 `NEEDS_REPLAN` 并停止，不自动重试。

## Contract / Data / Security Constraints

- API or event contract: 无产品 API/SPI、Spring Bean、配置、事件或 evidence schema 变化；仅
  existing source-seal identity 和传递 digest 值变化。
- data and migration: 无数据库 schema、业务数据或用户数据迁移；仅 run-owned fixture/evidence。
- compatibility and rollback: integration 可独立 revert，但 revert 会恢复已证明的 deterministic
  preflight failure；不得通过关闭 binding verifier 回滚。
- permissions and secrets: 限于已授权 v934 runner、evidence 和 PR #124；持久记录不保存 raw
  output、凭证、host path 或外部资源 identity。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1..AC-2 | blocker | Git topology、changed-path、raw SHA/top manifest/nested constant exact comparison | exact Cplan/Cint SHA 和三方 digest |
| AC-3 | blocker | `--verify-runner-seal-bindings`、完整 lifecycle suite、source-seal mutations | stable PASS、现有 case counts、所有 probe exit 0 |
| AC-4..AC-5 | critical | Step4/6 manifests、coverage/overlay、CI workflow 和 86-case negative、diff audit | exact manifest hashes、validator summaries、protected-path proof |
| AC-6 | blocker | Cdiag parent/path/two-field diff、fresh-clone preflight | exact Cint/Cdiag SHA、clean/non-shallow proof |
| AC-7 | blocker | 一次 fresh full Step4 diagnostic | run ID、lane/report/coverage/candidate/capsule/cleanup receipts |
| AC-8 | blocker | 成功后的 Cfreeze/formal/Step5-7 或失败记录 | commit/run IDs、verdict、停止边界 |

验证成本与循环控制：

- `<5m`：SHA/manifest、Python/JSON/Bash syntax、Git diff、exact binding preflight、coverage/
  overlay/CI static；每次相关字节变化后重跑。
- `5-30m`：完整 lifecycle suite 和 CI 86-case negative；只在 integration 字节冻结后各运行一次。
- `>60m`：fresh diagnostic、formal 及后续 release-authority；每个新 governed run ID 最多一次。
- reusable evidence: FSScript lifecycle 产品验收、report inventory `774+59/5709`、Step5 package
  117-case negative 和之前不依赖 Step4 manifest identity 的 focused 结果继续有效。
- rerun trigger: 仅 source/tooling/policy/selector/environment assumption 或 evidence identity 变化
  才使对应结果失效。
- stop/replan: exact preflight、Cdiag topology或任何昂贵运行失败，均不得原地修补重试；记录
  fail-closed 后设 `NEEDS_REPLAN`。

## Bug Context

- bug_source: regression-found during release-authority preflight.
- severity: critical/blocker.
- environment: PR #124；Cdiag `b8be71aecf8e7526cc5b13cb67426e968f2c5a9a`；run ID
  `step4-v934-fsscript-lifecycle-inventory-diagnostic-20260722-r6`。
- current_behavior: actual outer runner 和 top manifest 均为 `ccda7e7e...e37a57e`，nested
  lifecycle constant 仍为 `cf3979fc...f109e82`，canonical runner 在创建 run root 前返回
  `E_SOURCE_SEAL_BINDING`。
- expected_behavior: 四方 nested constants、top manifest rows 和 canonical raw bytes 精确一致；
  r6 所示 stale consumer 应在 Cdiag 前 focused preflight 被发现。
- reproduction_steps:
  1. 计算 outer runner raw SHA 并读取 Step4 manifest row；
  2. 读取 lifecycle tool 的 `OUTER_RUNNER_SHA256`；
  3. 执行 `run_log_lifecycle_negative_test.sh --verify-runner-seal-bindings`；
  4. 观察 stable `E_SOURCE_SEAL_BINDING` 和唯一 outer mismatch。
- reproduction_status: confirmed.
- existing_evidence:
  `docs/9.3.4/evidence/step-4/step4-v934-fsscript-lifecycle-inventory-diagnostic-20260722-r6-source-seal-preflight-fail-closed.md`。
- existing_tests: descriptor-bound four-runner binding positive/negative、完整 lifecycle suite、
  Step4/6 manifest 和 CI negative；缺口是上轮 Cdiag 前未显式执行 canonical exact-binding 入口。
- regression_protection: required；现有 source-seal tests 不删除，本 successor 把 canonical
  preflight 固定为 activation gate。
- waiver_reason_and_risk: N/A。

## Risks and Open Questions

- known_risks:
  - lifecycle tool hash 会改变 Step4 manifest identity，并继续传递到 Step6 CI bindings；漏更新任一
    consumer 会再次 fail closed；
  - 若 outer runner 在本事项中意外变化，批准的 exact target SHA 即失效，必须 replan；
  - full diagnostic 仍受 Docker/端口/runner image 环境影响，但一次性规则不区分环境与产品失败。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、根 `CLAUDE.md`、r6 fail-closed evidence、历史
  `BUG-step4-outer-runner-source-seal-binding-drift.md`、lifecycle validator 和 Step4/6 manifests。
- 先提交并 push 本 `APPROVED` Cplan；它不授权 runner。
- 随后只允许一个 diagnostic-ready integration commit；先运行 exact binding preflight，再完成
  lifecycle/manifest/coverage/overlay/CI focused validation。
- integration clean/pushed 后创建唯一 activation Cdiag；其 diff 只能是本文件两字段变化。
- 只从 Cdiag fresh non-shallow clone 启动一次新 diagnostic；不得复用 r6 run ID 或任何 partial
  evidence。
- 如需修改 outer runner、产品/测试/POM/report inventory/coverage/selector/DB set、安全边界，
  或新昂贵运行失败，将本文件设为 `NEEDS_REPLAN` 并停止。
- 成功后填写 `Implementation Result` 并设为 `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

> 由执行会话填写。当前为 approved Cplan，不授权 runner。

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- manual_or_experience_evidence: pending
- deviations: none
- residual_risks: pending
- readiness: APPROVED

## References

- r6 predecessor work item:
  `docs/9.3.4/workitems/BUG-v934-fsscript-lifecycle-report-inventory-successor-replan.md`
- r6 fail-closed record:
  `docs/9.3.4/evidence/step-4/step4-v934-fsscript-lifecycle-inventory-diagnostic-20260722-r6-source-seal-preflight-fail-closed.md`
- historical same-class bug:
  `docs/9.3.4/workitems/BUG-step4-outer-runner-source-seal-binding-drift.md`
- controlling release item:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
