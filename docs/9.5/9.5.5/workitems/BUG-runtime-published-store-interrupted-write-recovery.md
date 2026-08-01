---
doc_type: delivery-spec
delivery_type: bug
version: 9.5.5
ticket: BUG-runtime-published-store-interrupted-write-recovery
status: ULTRA_EXECUTING
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: user
approved_at: 2026-08-01
open_questions: []
---

# Delivery Spec: Published Store 中断写入安全恢复

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结 published artifact staging 与 attempt metadata temporary 的 ownership-bearing 新格式和 fail-closed
  recovery；只回收新格式中 owner/identity 可证明的中断写入，legacy/foreign/unknown 现场永远保留。
- canonical_path: `docs/9.5/9.5.5/workitems/BUG-runtime-published-store-interrupted-write-recovery.md`

## Goal

- version_goal: 消除新 publication 中断写入永久阻断 owned published store 的问题，为 lifecycle Console 提供稳定、
  可解释的健康状态，同时不引入 artifact retention/GC。
- target_outcome: 新 artifact staging 与 attempt atomic temporary 在产生潜在残留前先写入 store-bound ownership
  evidence；后续同进程 publication lock 内首次访问或重试时只清理 marker、storeId、attempt、target、schema 和
  filesystem type 全部匹配的 interrupted writes，任何不确定性继续 fail closed。
- critical_outcomes:
  - recovery 绝不 follow symlink、猜测 legacy filename、删除 foreign/corrupt/unknown 或 final immutable artifact。
  - marker 的创建顺序覆盖 staging/temp 的全部可见写入窗口；marker 自身 partial/corrupt 时零删除。
  - attempt final、artifact final、workspace/live/recovery/rollback evidence 与未提交 temporary 的身份严格区分。
  - recovery 幂等；删除中途失败后仍可诊断且下一次重试不扩大删除集合。
- success_is_sufficient_when: AC-1～AC-8 有真实 `@TempDir` crash-window/foreign/symlink/corrupt/idempotency evidence，
  lifecycle inventory 同步理解新 marker，focused/affected Runtime tests 和完整 diff checks 全绿。

## Scope

- in_scope:
  - 为新 artifact staging 和 attempt metadata temporary 增加 published-root/storeId/attempt-bound owner evidence。
  - 在既有 store synchronized + publication lock 调用链内恢复 owner 可证明的 interrupted writes。
  - inventory 新增 redacted marker/object state 与 stable blocked reasons；read-only API 自身仍零 mutation。
  - production store failure injection/真实临时目录 tests、architecture/9.5.5 result/signoff。
- affected_modules: `foggy-runtime-api`、`docs/architecture`、`docs/9.5/9.5.5`。
- external_dependencies: none；不得新增 Maven、数据库、Node 或外部服务依赖。

## Non-Goals

- out_of_scope:
  - final artifact/attempt、obsolete revision、terminal evidence、tombstone 的 retention/GC 或 age/grace policy。
  - 公共 cleanup API、Console mutation button、scheduler/background worker、startup root initialization、audit DB。
  - legacy temporary 的 migration/adoption、跨进程/shared-NFS coordination、OS power-loss 全时序证明。
- do_not_touch: Console、Engine/Model SPI、launcher、POM/dependencies、数据库、真实 `.foggy-runtime`、历史 acceptance。
- non_blocking_or_waivable_items: 外部 writer/multi-process 与所有 power-loss 时序属于已知边界；不可豁免
  ownership/reference/final-artifact/foreign/symlink guards。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| owner evidence 必须在可回收对象出现前先持久化 | 避免 crash 留下仅凭名字判断的对象 | partial/corrupt marker 保留并阻断，不猜测 |
| recovery 仅发生在既有 publication 操作/重试的锁内 store validation | 不新增 scheduler 或启动时隐式初始化 root | missing root 仍不创建；inventory GET 仍只读 |
| legacy staging/temp 无新 marker 时继续 preserve | 历史格式缺少独立 storeId wrapper | 兼容 accepted SPIKE characterization |
| final artifact/attempt 永不由本 recovery 删除 | interrupted-write recovery 不是 retention | 即使 terminal、old 或 unreferenced 也不处理 |
| cleanup 失败即 fail closed | 无法证明收敛时不得继续 publication | stable error，不泄漏 path/storeId/raw exception |
| inventory 识别新 owner marker | Console/操作者需要区分 recoverable 与 unknown | 仍不由 inventory 执行 mutation |

## Acceptance Criteria

- [ ] AC-1: 新 artifact staging owner evidence 在 staging directory 前建立并绑定 storeId、staging identity、attempt、
  workspace、Namespace、Bundle、candidate revision；成功 commit 后不污染 final artifact layout。
- [ ] AC-2: 新 attempt temporary owner evidence 在 JSON temporary 前建立并绑定 storeId、temporary/final identity 与
  attempt；atomic move 成功后 marker 被清理，final attempt schema 兼容不变。
- [ ] AC-3: valid marker + missing/partial owned staging/temp 在下一次 locked store access 中被幂等清理；marker-only、
  final-already-committed 和 cleanup retry 均安全收敛。
- [ ] AC-4: marker missing/corrupt、storeId/attempt/target mismatch、unknown entry、foreign file/directory 与任何 symlink
  一律 preserve 并阻断；sentinel 和 link target byte-for-byte 不变。
- [ ] AC-5: recovery 不删除或改写 final artifact/attempt、live/previous/rollback/recovery evidence；不使用 age/status/
  filename shortcut，不引入 retention/GC。
- [ ] AC-6: lifecycle inventory redacted、deterministic 地报告新 marker/recoverable interrupted state；inventory scan
  前后 mtime/content/layout 不变，legacy temporary 仍为 `UNKNOWN_PRESERVE`。
- [ ] AC-7: error/code/phase 不泄漏 absolute path、storeId、模型内容、auth/连接串/raw exception；cleanup failure
  `safeToAutoRepair=false` 且不继续 publication mutation。
- [ ] AC-8: compile、focused store/publication/inventory tests、affected `Runtime*Test`、完整 tracked/untracked diff checks
  通过；无 Console/Engine/launcher/POM/database/真实 store 越界。

## Contract / Data / Security Constraints

- API or event contract: 不新增 public API；既有 lifecycle inventory 仅 additive object type/reason。
- data and migration: 新 marker 是 forward-only ephemeral schema；不迁移 legacy，不改变 final artifact/attempt schema。
- compatibility and rollback: 旧 Runtime 看见新 in-flight marker 可能 fail closed；正常成功写入不留下 marker。代码回滚无需
  迁移 final data，残留新 marker 由旧版本 preserve。
- permissions and secrets: marker 只含 opaque lifecycle identity，不含模型内容、path、token、连接串或 catalog content。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/2/3 | must-pass | critical | production store + `@TempDir` phase/restart/idempotency tests | accepted SPIKE characterization | marker ordering、owned cleanup、final compatibility |
| AC-4/5/7 | must-pass | critical | foreign/symlink/corrupt/mismatch/failure injection | publication negative baseline | zero follow/delete + fail-closed |
| AC-6 | must-pass | critical | inventory service classification + fingerprint | accepted inventory tests | recoverable vs legacy unknown、zero mutation |
| AC-8 | must-pass | major | compile、focused、affected Runtime lane、diff checks | current Runtime baseline | exact commands/counts/results |

## Validation Budget and Evidence Sufficiency

- assurance_level: elevated；涉及 filesystem 删除，即使仅限 ephemeral objects 也属于不可逆 mutation。
- lightweight_validation: spec/source review、compile、tracked/untracked whitespace/path audit，单次 `<5m`。
- medium_validation:
  - focused：publication store、workspace publish、lifecycle inventory，预计 `5-15m`。
  - affected：`Runtime*Test`，预计 `5-20m`，最终候选最多一次。
- expensive_validation: none；不运行 DB matrix、Console、完整 release chain。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: 若需 final artifact/evidence retention、public cleanup API、scheduler、migration、跨进程锁，
  设置 `NEEDS_REPLAN`。
- maximum_expensive_attempts: 0
- reusable_evidence: accepted lifecycle SPIKE characterization、inventory signoff、9.5.3 publication recovery 与 9.5.4
  rollback；仅 store write/recovery 和 inventory marker input 需重验。
- stop_when_evidence_is_sufficient: 全 crash-window/ownership/zero-delete guards 有自动化断言，focused+affected+diff 全绿。
- validation_not_required: Console/Playwright、Engine/launcher、DB matrix、真实 deployment/shared-NFS/multi-process、
  authority/replay/rehearsal/tag/release/publish。

## Waiver Policy

- waivable_items: none
- authorized_role: owner
- non_waivable_guards: ownership、final/live/recovery/rollback preservation、symlink/foreign zero-delete、evidence truthfulness。
- required_risk_record: legacy unknown 持续阻断、single-process/local-filesystem 边界。

## Bug Context

- bug_source: acceptance-found
- severity: major
- environment: Runtime-managed local published store after process interruption or failed atomic write.
- current_behavior: partial `.staging-*` 或 `attempt.json.tmp-*` 缺少独立 ownership wrapper，restart/next access 严格
  validation 后永久阻断且无法安全自动处理。
- expected_behavior: 新格式中 owner 可证明的 interrupted writes 可幂等恢复；legacy/unknown 继续保留并诊断。
- reproduction_steps: production store 在 artifact resource write 或 attempt atomic rename 前失败，重建 store 后访问。
- reproduction_status: confirmed
- existing_evidence: accepted SPIKE 的两个真实 `@TempDir` characterization。
- existing_tests: `RuntimeAuthoringPublicationStoreTest`、`RuntimeArtifactLifecycleInventoryServiceTest`。
- regression_protection: required
- waiver_reason_and_risk: N/A

## Risks and Open Questions

- known_risks: marker 自身 partial 时仍需人工保留；外部进程并发写不受 publication lock 约束。
- open_questions: none

## Ultra Execution Contract

- 先读本 spec、accepted SPIKE/inventory、Runtime lifecycle architecture 和 store/inventory/tests。
- 先建立 crash-window/failure tests，再实现最小 marker/recovery；不得放宽现有 final layout/hash/owner validation。
- 状态开始时改 `ULTRA_EXECUTING`；如需 public cleanup API、retention 或 Console mutation，转 `NEEDS_REPLAN`。
- 记录精确验证命令/结果；达到 sufficiency 后停止，不运行大型链路。
- 完成后填写 `Implementation Result` 并改 `READY_FOR_SIGNOFF`，不得自行设置 `ACCEPTED`。

## Implementation Result

> 由 Ultra 执行会话填写。

- implementation_summary:
- changed_paths:
- tests_and_results:
- manual_or_experience_evidence:
- deviations: none
- residual_risks:
- reused_evidence:
- omitted_validation_and_reason:
- readiness: READY_FOR_SIGNOFF | NEEDS_REPLAN | BLOCKED

## References

- requirement / issue: accepted lifecycle SPIKE P0 recommendation；用户批准继续推进至 Console 可验收。
- architecture / glossary: `docs/architecture/runtime-and-model-lifecycle.md`
- related work items:
  - `docs/9.5/9.5.5/workitems/SPIKE-runtime-artifact-store-lifecycle-foundations.md`
  - `docs/9.5/9.5.5/workitems/FEATURE-runtime-artifact-lifecycle-inventory.md`
